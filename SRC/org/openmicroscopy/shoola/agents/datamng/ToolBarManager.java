/*
 * org.openmicroscopy.shoola.agents.datamng.ToolBarManager
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;

//Third-party libraries

//Application-internal dependencies

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
class ToolBarManager
	implements ActionListener
{
	/** Action command ID. */
	private static final int		CREATE_P = 0, CREATE_D = 1, CREATE_I = 2;
	
	private DataManagerCtrl control;
	private ToolBar			view;
	
	ToolBarManager(DataManagerCtrl control, ToolBar view)
	{
		this.control = control;
		this.view = view;
		attachListeners();
	}

	/** Attach the listeners. */
	private void attachListeners()
	{
		JButton pButton = view.getProjectButton(), 
				dButton = view.getDatasetButton(),
				iButton = view.getImageButton();
		pButton.setActionCommand(""+CREATE_P);
		pButton.addActionListener(this);	
		dButton.setActionCommand(""+CREATE_D);
		dButton.addActionListener(this);
		iButton.setActionCommand(""+CREATE_I);
		iButton.addActionListener(this);
	}

	/** Handle event fired by buttons. */
	public void actionPerformed(ActionEvent e)
	{
		try {
			int cmd = Integer.parseInt(e.getActionCommand());
			switch (cmd) {
				case CREATE_P:
					control.createProject(); break;
				case CREATE_D:
					control.createDataset(); break;
				case CREATE_I:
					break;
			}
		} catch(NumberFormatException nfe) { throw nfe; }
	}
}
