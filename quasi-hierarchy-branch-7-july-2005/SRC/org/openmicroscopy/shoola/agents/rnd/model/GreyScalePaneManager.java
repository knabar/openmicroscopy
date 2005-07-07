/*
 * org.openmicroscopy.shoola.agents.rnd.model.GreyScaleManager
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
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JRadioButton;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.rnd.RenderingAgtCtrl;
import org.openmicroscopy.shoola.agents.rnd.editor.ChannelEditor;
import org.openmicroscopy.shoola.util.ui.UIUtilities;

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
class GreyScalePaneManager
	implements ActionListener
{
	private RenderingAgtCtrl	eventManager;
	
	void setEventManager(RenderingAgtCtrl eventManager)
	{
		this.eventManager = eventManager;
	}
	
	/** Attach listeners. */
	void attachObjectListener(Object component, int index)
	{
		AbstractButton b = null;
		if (component instanceof JButton) 
			b = (JButton) component;	
		else if (component instanceof JRadioButton)
			b = (JRadioButton) component;
		b.addActionListener(this);
		b.setActionCommand(""+index);
	}
	
	/** Handle events. */
	public void actionPerformed(ActionEvent e)
	{
		Object obj = e.getSource();
		int index = -1;
		try {
            index = Integer.parseInt(e.getActionCommand());
			if (obj instanceof JRadioButton) {
				eventManager.setActive(index); 
			} else 
				UIUtilities.centerAndShow(new ChannelEditor(eventManager, 
									eventManager.getChannelData(index)));
		} catch(NumberFormatException nfe) {  
			throw new Error("Invalid Action ID "+index, nfe);
		}    
	}
	
}
