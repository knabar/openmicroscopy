/*
 * org.openmicroscopy.shoola.agents.dataBrowser.browser.BrowserModel 
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2008 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package org.openmicroscopy.shoola.agents.dataBrowser.browser;


//Java imports
import java.awt.Cursor;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.dataBrowser.Colors;
import org.openmicroscopy.shoola.agents.dataBrowser.DataBrowserAgent;
import org.openmicroscopy.shoola.agents.dataBrowser.layout.Layout;
import org.openmicroscopy.shoola.agents.dataBrowser.layout.LayoutFactory;
import org.openmicroscopy.shoola.agents.dataBrowser.visitor.NodesFinder;
import org.openmicroscopy.shoola.agents.dataBrowser.visitor.ResetNodesVisitor;
import org.openmicroscopy.shoola.agents.events.iviewer.ViewImage;
import org.openmicroscopy.shoola.env.event.EventBus;
import org.openmicroscopy.shoola.util.ui.component.AbstractComponent;
import pojos.DataObject;
import pojos.ImageData;

/** 
 * Implements {@link Browser} to maintain presentation state, thus acting
 * as the Model in MVC.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
class BrowserModel
	extends AbstractComponent
	implements Browser
{
	
	/**
	 * Flag indicating to display the data related to a node
	 * when the user mouses over the node.
	 */
	private boolean				mouseOver;
	
	/** 
	 * Flag to control the zoom action when the user mouses over an 
	 * {@link ImageNode}. 
	 */
	private boolean         	rollOver;
	
	/** 
	 * Tells if a thumbnail has been selected in the case the 
	 * last selected display is an {@link ImageNode}. 
	 */
	private boolean         	thumbSelected;
	
	/** Position of the last pop-up trigger within the browser. */
	private Point           	popupPoint;
	
	/** Contains all visualization trees, our View. */
	private RootDisplay     	rootDisplay;
	
	/** The selected layout. */
	private Layout             	selectedLayout;
	
	/**
	 * Tells if more than one {@link ImageNode}s are selected.
	 * Doesn't really make sense to select other type of data objects.
	 */
	private boolean         	multiSelection;
	
	/** The selected nodes. */
	private Set<ImageDisplay>	selectedDisplays;
	
	/** Indicates if the image's title bar is visible. */
	private boolean         	titleBarVisible;
	
	/** The node on which the mouse was located before exited. */
	private ImageDisplay    	rollOverNode;
	
	/** The collection of original images. */
	private Set<ImageDisplay>	originalNodes;
	
	/**
	 * Adds the children of the passed node to its internal desktop.
	 * This method should be invoked when user switches between layout.
	 * 
	 * @param node The node to handle.
	 */
	private void addToDesktop(ImageDisplay node)
	{
	    if (node instanceof ImageNode) return;
	    JComponent desktop = node.getInternalDesktop();
	    Set children = node.getChildrenDisplay();
	    if (children == null) return;
	    Iterator i = children.iterator();
	    ImageDisplay child;
	    while (i.hasNext()) {
	        child = (ImageDisplay) i.next();
	        if (!node.containsImages()) addToDesktop(child);
	        else desktop.add(child);
	    }
	}
	
	/**
	 * Creates a new instance.
	 * 
	 * @param view The root display of the visualization trees. Each child node
	 *              is the top node of a visualization tree.
	 *              Mustn't be <code>null</code>.
	 */
	BrowserModel(RootDisplay view)
	{
	    super();
	    if (view == null) throw new NullPointerException("No view.");
	    rootDisplay = view;
	    selectedDisplays = new HashSet<ImageDisplay>();
	    originalNodes = new HashSet<ImageDisplay>();
	    titleBarVisible = true;
	    Set nodes = rootDisplay.getChildrenDisplay();
	    Iterator i = nodes.iterator();
	    while (i.hasNext()) {
			originalNodes.add((ImageDisplay) i.next());
		}
	}
	
    /**
     * Sets the title and the color of the selected node.
     * 
     * @param newNode The selected node.
     * @param nodes   The previously selected nodes.
     */
    void onNodeSelected(ImageDisplay newNode, Set nodes)
    {
        if (newNode == null) return;
        rootDisplay.setTitle(currentPathString(newNode));
        //paint the nodes
        List<ImageDisplay> selected = new ArrayList<ImageDisplay>();
        selected.add(newNode);
		if (isMultiSelection() || nodes == null)
			setNodesColor(selected, null); 
		else setNodesColor(selected, nodes);
    }
    
	/**
	 * Sets the specified <code>node</code> to be the currently selected
	 * node in the visualization tree.
	 * Sets it to <code>null</code> to indicate no node is currently selected.
	 *  
	 * @param node           	The node to become the currently selected node.
	 * 						 	Pass <code>null</code> only when refreshing the
	 * 						 	display.
	 * @param multiSelection	Pass <code>false</code> to indicate that only 
	 * 							one node is selected, <code>true</code> 
	 * 							otherwise.
	 * @param fireProperty		Pass <code>true</code> to fire a property, 
	 * 							<code>false</code> otherwise.
	 */
	void setSelectedDisplay(ImageDisplay node, boolean multiSelection, boolean
							fireProperty)
	{
		//if (node == null) return;
	    thumbSelected = false;
	    popupPoint = null;
	    this.multiSelection = multiSelection;
	    Set<ImageDisplay> oldValue = 
	    					new HashSet<ImageDisplay>(selectedDisplays.size());
	    Iterator i = selectedDisplays.iterator();
	    while (i.hasNext())
	        oldValue.add((ImageDisplay) i.next());
	    
	    if (!multiSelection) selectedDisplays.removeAll(selectedDisplays);
	    if (node != null) selectedDisplays.add(node);
	    if (fireProperty)
	    	firePropertyChange(SELECTED_DISPLAY_PROPERTY, oldValue, node);
	    else onNodeSelected(node, oldValue);
	}
	
	/**
	 * String-ifies the path from the specified node to the
	 * {@link #rootDisplay}.
	 * 
	 * @param parent The node to start from.
	 * @return The above described string.
	 */
	String currentPathString(ImageDisplay parent)
	{
	    StringBuffer buf = new StringBuffer();
	    String title;
	    while (parent != null && !(parent instanceof RootDisplay)) {
	        title = parent.getTitle();
	        if (title == null || title.length() == 0) title = "[..]";
	        if (parent instanceof ImageSet) buf.insert(0, " > ");
	        buf.insert(0, title);
	        parent = parent.getParentDisplay();
	    }
	    return buf.toString();
	}
	
	/** 
	 * Fires a property whose new value if the passed object.
	 * 
	 * @param propName  The name of the property.
	 * @param node      The new value.
	 */
	void setNodeForProperty(String propName, Object node)
	{
	    firePropertyChange(propName, null, node);
	}
	
	/**
	 * Sets the node which has to be zoomed.
	 * 
	 * @param newNode The node to zoom.
	 */
	void setRollOverNode(ImageNode newNode)
	{
	    ImageDisplay previousNode = rollOverNode;
	    rollOverNode = newNode;
	    firePropertyChange(ROLL_OVER_PROPERTY, previousNode, newNode);
	}

	/**
	 * Sets the color of the selected and deselected nodes.
	 * 
	 * @param toSelect		The collection of selected nodes.
	 * @param toDeselect	The collection of deselected nodes.
	 */
	void setNodesColor(Collection toSelect, Collection toDeselect)
    {
    	//paint the nodes
        Colors colors = Colors.getInstance();
        Iterator i = toSelect.iterator();
        ImageDisplay node;
        while (i.hasNext()) {
			node = (ImageDisplay) i.next();
			node.setHighlight(colors.getSelectedHighLight(node));	
		}
        if (toDeselect == null) return;
        i = toDeselect.iterator();
        while (i.hasNext()) {
        	node = (ImageDisplay) i.next();
            if (node != null && !toSelect.contains(node))
            	node.setHighlight(colors.getDeselectedHighLight(node));
        }
    }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getRootNodes()
	 */
	public Set getRootNodes() { return rootDisplay.getChildrenDisplay(); }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getSelectedDisplays()
	 */
	public Collection getSelectedDisplays() { return selectedDisplays; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setSelectedDisplay(ImageDisplay)
	 */
	public void setSelectedDisplay(ImageDisplay node)
	{
	    setSelectedDisplay(node, false, true);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setSelectedDisplays(ImageDisplay[])
	 */
	public void setSelectedDisplays(ImageDisplay[] nodes)
	{
		for (int i = 0; i < nodes.length; i++) 
			setSelectedDisplay(nodes[i], true, true);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getLastSelectedDisplay()
	 */
	public ImageDisplay getLastSelectedDisplay()
	{ 
	    Iterator i = selectedDisplays.iterator();
	    int index = 0;
	    while (i.hasNext()) {
	        if (index == (selectedDisplays.size()-1)) 
	            return (ImageDisplay) i.next();
	        index++;
	    }
	    return null;  
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setThumbSelected(boolean, ImageNode)
	 */
	public void setThumbSelected(boolean selected, ImageNode node)
	{
	    if (node == null)
	        throw new IllegalArgumentException("No node");
	    if (!selected) return;
	    popupPoint = null;
	    thumbSelected = selected;
	    firePropertyChange(THUMB_SELECTED_PROPERTY, null, node);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#isThumbSelected()
	 */
	public boolean isThumbSelected() { return thumbSelected; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setPopupPoint(java.awt.Point)
	 */
	public void setPopupPoint(Point p)
	{
	    thumbSelected = false;
	    Object oldValue = popupPoint;
	    popupPoint = p;
	    firePropertyChange(POPUP_POINT_PROPERTY, oldValue, p);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getPopupPoint()
	 */
	public Point getPopupPoint() { return popupPoint; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getImages()
	 */
	public Set<ImageData> getImages()
	{ 
	    //Note: avoid caching b/c we don't know yet what we are going
	    //to do with updates
	    ImageFinder finder = new ImageFinder();
	    accept(finder, ImageDisplayVisitor.IMAGE_NODE_ONLY);
	    return finder.getImages(); 
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getImageNodes()
	 */
	public Set getImageNodes()
	{ 
	    //Note: avoid caching b/c we don't know yet what we are going
	    //to do with updates
	    ImageFinder finder = new ImageFinder();
	    accept(finder, ImageDisplayVisitor.IMAGE_NODE_ONLY);
	    return finder.getImageNodes(); 
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#accept(ImageDisplayVisitor)
	 */
	public void accept(ImageDisplayVisitor visitor) 
	{
	    rootDisplay.accept(visitor, ImageDisplayVisitor.ALL_NODES);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#accept(ImageDisplayVisitor, int)
	 */
	public void accept(ImageDisplayVisitor visitor, int algoType) 
	{
	    rootDisplay.accept(visitor, algoType);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getUI()
	 */
	public JComponent getUI() { return rootDisplay; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setSelectedLayout(Layout)
	 */
	public void setSelectedLayout(Layout layout)
	{
		if (layout == null) return;
		Layout oldLayout = selectedLayout;
	    switch (layout.getIndex()) {
	        case LayoutFactory.SQUARY_LAYOUT:
	        case LayoutFactory.FLAT_LAYOUT:  
	            selectedLayout = layout;
	            break;
	    }
	    firePropertyChange(LAYOUT_PROPERTY, oldLayout, selectedLayout);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getSelectedLayout()
	 */
	public Layout getSelectedLayout() { return selectedLayout; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#isMultiSelection()
	 */
	public boolean isMultiSelection() { return multiSelection; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#isTitleBarVisible()
	 */
	public boolean isTitleBarVisible() { return titleBarVisible; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setTitleBarVisible(boolean)
	 */
	public void setTitleBarVisible(boolean b) { titleBarVisible = b; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setRollOver(boolean)
	 */
	public void setRollOver(boolean rollOver)
	{ 
		setRollOverNode(null);
		this.rollOver = rollOver; 
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#isRollOver()
	 */
	public boolean isRollOver() { return rollOver; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#isMouseOver()
	 */
	public boolean isMouseOver() { return mouseOver; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setMouseOver(boolean)
	 */
	public void setMouseOver(boolean b) { mouseOver = b; }
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#resetChildDisplay()
	 */
	public void resetChildDisplay()
	{
	    rootDisplay.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	    Set rootChildren = rootDisplay.getChildrenDisplay();
	    JComponent desktop = rootDisplay.getInternalDesktop();
	    desktop.removeAll();
	    Iterator i;
	    switch (selectedLayout.getIndex()) {
			case LayoutFactory.SQUARY_LAYOUT:
				 i = rootChildren.iterator();
			        ImageDisplay child;
			        while (i.hasNext()) {
			            child = (ImageDisplay) i.next();
			            desktop.add(child);
			            addToDesktop(child);
			        }
				break;
	
			case LayoutFactory.FLAT_LAYOUT:
				 i = getImageNodes().iterator();
			        while (i.hasNext()) 
			            desktop.add((ImageDisplay) i.next());   
		}
	    rootDisplay.setCursor(
	            Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setSelectedNodes(List)
	 */
	public void setSelectedNodes(List<DataObject> nodes)
	{
		if (nodes == null || nodes.size() == 0) return;
		NodesFinder finder = new NodesFinder(nodes);
		rootDisplay.accept(finder);
		List<ImageDisplay> found = finder.getFoundNodes();
		//to reset color if parent is selected.
		setNodesColor(found, getSelectedDisplays());
		boolean b = found.size() > 1;
		Iterator<ImageDisplay> i = found.iterator();
		ImageDisplay node = null;
		while (i.hasNext()) {
			node = i.next();
			setSelectedDisplay(node, b, false);
		}
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setFilterNodes(Collection)
	 */
	public void setFilterNodes(Collection<ImageDisplay> nodes)
	{
		ResetNodesVisitor visitor = new ResetNodesVisitor(nodes, true);
		rootDisplay.accept(visitor, ImageDisplayVisitor.IMAGE_SET_ONLY);
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#showAll()
	 */
	public void showAll()
	{
		setFilterNodes(getImageNodes());
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getOriginal()
	 */
	public Set<DataObject> getOriginal()
	{
		Set<DataObject> nodes = new HashSet<DataObject>();
		Iterator<ImageDisplay> i = originalNodes.iterator();
		Object ho;
		while (i.hasNext()) {
			ho = i.next().getHierarchyObject();
			if (ho instanceof DataObject)
			nodes.add((DataObject) ho);
		}
		return nodes;
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getVisibleImages()
	 */
	public Set<ImageData> getVisibleImages()
	{
		//Note: avoid caching b/c we don't know yet what we are going
		//to do with updates
	    ImageFinder finder = new ImageFinder();
	    accept(finder, ImageDisplayVisitor.IMAGE_SET_ONLY);
	    return finder.getVisibleImages(); 
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#getVisibleImageNodes()
	 */
	public List<ImageNode> getVisibleImageNodes()
	{
		//Note: avoid caching b/c we don't know yet what we are going
		//to do with updates
	    ImageFinder finder = new ImageFinder();
	    accept(finder, ImageDisplayVisitor.IMAGE_SET_ONLY);
	    return finder.getVisibleImageNodes();
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setNodesSelection(List)
	 */
	public void setNodesSelection(List<ImageDisplay> nodes)
	{
		if (nodes == null) return;
		setNodesColor(nodes, getSelectedDisplays());
		Iterator<ImageDisplay> i = nodes.iterator();
		boolean multiSelection = nodes.size() > 1;
		
		while (i.hasNext()) 
			setSelectedDisplay(i.next(), multiSelection, true);
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#removeSelectedDisplay(ImageDisplay)
	 */
	public void removeSelectedDisplay(ImageDisplay node)
	{
		if (node == null) return;
		List<ImageDisplay> nodes = new ArrayList<ImageDisplay>(1);
		nodes.add(node);
		setNodesColor(getSelectedDisplays(), nodes);
		selectedDisplays.remove(node);
		firePropertyChange(UNSELECTED_DISPLAY_PROPERTY, null, node);
	}

	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#viewDisplay(ImageDisplay)
	 */
	public void viewDisplay(ImageDisplay node)
	{
		if (node == null) return;
		if (node instanceof ImageNode) {
			EventBus bus = DataBrowserAgent.getRegistry().getEventBus();
	    	bus.post(new ViewImage((ImageData) node.getHierarchyObject(), 
	    				null));
		}
	}
	
	/**
	 * Implemented as specified by the {@link Browser} interface.
	 * @see Browser#setComponentTitle(String)
	 */
	public void setComponentTitle(String title)
	{
		rootDisplay.setTitle(title);
	}
	
}
