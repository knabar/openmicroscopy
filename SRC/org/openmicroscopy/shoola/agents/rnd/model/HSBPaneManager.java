/*
 * org.openmicroscopy.shoola.agents.rnd.model.HSBMappingManager
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

package org.openmicroscopy.shoola.agents.rnd.model;


//Java imports
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.rnd.RenderingAgtCtrl;
import org.openmicroscopy.shoola.agents.rnd.editor.ChannelEditor;
import org.openmicroscopy.shoola.agents.rnd.metadata.ChannelData;
import org.openmicroscopy.shoola.util.ui.ColoredButton;
/** 
 * 
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
class HSBPaneManager
	implements ActionListener, ItemListener
{
	private HSBPane	view;
	
	private RenderingAgtCtrl	eventManager;
	
	HSBPaneManager(HSBPane view)
	{
		this.view = view;
	}
	
	HSBPane getView()
	{
		return view;
	}
	
	RenderingAgtCtrl getEventManager()
	{
		return eventManager;
	}
	
	
	
	/** Reference to the main frame, use to pop-up the Color dialog widget. */
	JFrame getReferenceFrame()
	{
		return (JFrame) eventManager.getRegistry().getTopFrame().getFrame();
	}
	
	void setEventManager(RenderingAgtCtrl eventManager)
	{
		this.eventManager = eventManager;
	}
	
	void attachObjectListener(Object component, int index)
	{
		AbstractButton ab = null;
		if (component instanceof JButton || component instanceof ColoredButton) 
		{
			ab = (JButton) component;
			ab.addActionListener(this);
		} else if (component instanceof JCheckBox) {
			ab = (JCheckBox) component;
			ab.addItemListener(this);
		}
		ab.setActionCommand(""+index);
	}

	/** Handle events fired by component, */
	public void actionPerformed(ActionEvent e)
	{ 
		String s = (String) e.getActionCommand();
		Object component = (Object) e.getSource();
		try {
			int index = Integer.parseInt(s);
			if (component instanceof ColoredButton) showColorChooser(index);
			else showChannelInfo(index);
		} catch(NumberFormatException nfe) {
				throw nfe;  //just to be on the safe side...
		}    
	}

	public void itemStateChanged(ItemEvent e)
	{
		JCheckBox box = (JCheckBox) e.getSource();
		int index = Integer.parseInt((String) box.getActionCommand());
		if (e.getStateChange()== ItemEvent.SELECTED) 
			mapChannel(index);
		else clearMapping(index);
	}
	
	/**
	 * Pop up the colorChooserDialog widget.
	 * 
	 * @param index		wavelength index.
	 */
	private void showColorChooser(int index)
	{
		//test
		int[] rgba = new int[4];
		rgba[0] = 124;
		rgba[1] = 124;
		rgba[2] = 124;
		rgba[3] = 255;	
		showDialog(new ColorChooser(this, rgba));
	}
	
	/**
	 * Pop up the wavelength info editor.
	 * 
	 * @param index		wavelength index.
	 */
	private void showChannelInfo(int index) 
	{
		ChannelData[] cd = eventManager.getChannelData();
		showDialog(new ChannelEditor(eventManager, cd[index]));
	}
	
	/**
	 * Map the specified wavelength.
	 * Forward event to {@link RenderingAgtCtrl}.
	 * 
	 * @param index	wavelength index to be mapped.
	 */
	private void mapChannel(int index)
	{
		
	}
	
	/**
	 * Unmap the specified wavelength.
	 * Forward event to {@link RenderingAgtCtrl}.
	 * @param index	wavelength index.
	 */
	private void clearMapping(int index)
	{
	}
	
	/** Forward event to {@link RenderingAgtUIF}. */
	private void showDialog(JDialog dialog)
	{
		eventManager.showDialog(dialog);
	}
	
}
