/*
 * org.openmicroscopy.shoola.agents.datamng.PopupMenu
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2004 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */
 
package org.openmicroscopy.shoola.agents.datamng;

//Java imports
import java.awt.Font;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.BorderFactory;
import javax.swing.border.BevelBorder;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.env.config.Registry;
import pojos.DataObject;

/** 
 * The UI of the context pop-up menu used within this agent's UI. 
 * Provides buttons for accessing the properties of an object (a project, 
 * dataset, categoryGroup, category or image), viewing an image, 
 * browsing a project/dataset/categoryGroup/category, and reloading
 * data from the DB.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author  <br>Andrea Falconi &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:a.falconi@dundee.ac.uk">
 * 					a.falconi@dundee.ac.uk</a>
 * @version 2.2
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 * @since OME2.2
 */
class TreePopupMenu
	extends JPopupMenu
{
    
    static final String             VIEW = "View", BROWSE = "Browse";
    
    static final String             ADD_ELEMENT = "New";
    
	/** This UI component's controller and model. */
	private TreePopupMenuManager	manager;
	
	/** Holds the configuration entries. */
	private Registry				config;
	
	/** 
	 * Button to bring up the property sheet of an object (project, dataset,
     * categoryGroup, category or image).
	 */
	JMenuItem   					properties;
	
	/** Button to view a project, dataset, categoryGroup, category or image. */
	JMenuItem   					view;

	/** Button to reload data from the DB. */
	JMenuItem   					refresh;

	/** Button to annotate a dataset or an image. */
	JMenuItem						annotate;
	
	/** Button to import images. */
	JMenuItem						importImg;
    
    JMenuItem                       newElement;
    
		
	/** 
	 * Creates a new instance.
	 *
	 * @param agentCtrl   The agent's control component.
	 */
	TreePopupMenu(DataManagerCtrl agentCtrl, Registry r) 
	{
		this.config = r;
		initProperties();
		initView();
		initRefresh();
		initAnnotate();
		initImportImage();
        initAddElement();
		manager = new TreePopupMenuManager(this, agentCtrl);
		buildGUI() ;
	}

	/** 
	 * Sets the object (project, dataset, categoryGroup, category or image) 
     * the menu is going to operate on. 
	 *
	 * @param   t  The object for which the menu has to be brought up.
	 */
	void setTarget(DataObject t) { manager.setTarget(t); }
    
    /** 
     * Sets the pane index, One of the constants defined by 
     * {@link DataManagerCtrl} i.e. {@link DataManagerCtrl#FOR_HIERARCHY}, 
     * {@link DataManagerCtrl#FOR_IMAGES} or 
     * {@link DataManagerCtrl#FOR_CLASSIFICATION}
     */
    void setIndex(int index) { manager.setIndex(index); }
    
    private void initAddElement()
    {
        IconManager icons = IconManager.getInstance(config);
        newElement = new JMenuItem(ADD_ELEMENT,
                    icons.getIcon(IconManager.CREATE_PROJECT));
        initMenuItem(newElement, true); 
    }
    
	/** Creates and initializes the properties button. */
	private void initProperties() 
	{
		IconManager icons = IconManager.getInstance(config);
		properties = new JMenuItem("Properties", 
									icons.getIcon(IconManager.PROPERTIES));
		initMenuItem(properties, false); 
	}

	/** Creates and initializes the view button. */
	private void initView() 
	{
		IconManager icons = IconManager.getInstance(config);
		view = new JMenuItem("View", icons.getIcon(IconManager.VIEWER));
		initMenuItem(view, false);
	}

	/** Creates and initializes the refresh button. */
	private void initRefresh() 
	{
		IconManager icons = IconManager.getInstance(config);
		refresh = new JMenuItem("Refresh", icons.getIcon(IconManager.REFRESH));
		initMenuItem(refresh, true);
	}

	/** Creates and initializes the annotate button. */
	private void initAnnotate()
	{
		IconManager icons = IconManager.getInstance(config);
		annotate = new JMenuItem("Annotate", 
								icons.getIcon(IconManager.ANNOTATE));
        initMenuItem(annotate, false);
	}
	
	/** Creates and initializes the importImage button. */
	private void initImportImage()
	{
		IconManager icons = IconManager.getInstance(config);
		importImg = new JMenuItem("Import", 
								icons.getIcon(IconManager.IMPORT_IMAGE));
        initMenuItem(importImg, false);
	}
	
    /** Set menuItem defaults. */
    private void initMenuItem(JMenuItem item, boolean b)
    {
        item.setBorder(null);
        item.setFont((Font) config.lookup("/resources/fonts/Labels"));
        item.setForeground(DataManagerUIF.STEELBLUE);
        item.setEnabled(b);
    }
    
	/** Builds and lays out the GUI. */
	private void buildGUI() 
	{
		setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		add(properties);
		add(view);
		//add(annotate);
		//add(importImg);
        add(newElement);
		add(refresh);
	}

}
