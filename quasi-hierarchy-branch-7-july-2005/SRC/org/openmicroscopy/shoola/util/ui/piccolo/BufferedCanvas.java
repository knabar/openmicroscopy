/*
 * org.openmicroscopy.shoola.agents.zoombrowser.piccolo.BufferedCanvas
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 Open Microscopy Environment
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




/*------------------------------------------------------------------------------
 *
 * Written by:    Harry Hochheiser <hsh@nih.gov>
 *
 *------------------------------------------------------------------------------
 */

package org.openmicroscopy.shoola.util.ui.piccolo;

//Java imports

//Third-party libraries
import org.openmicroscopy.shoola.util.ui.Constants;

import edu.umd.cs.piccolo.PCanvas;
import edu.umd.cs.piccolo.util.PBounds;

//Application-internal dependencies

/** 
 * A Canvas that has a buffered bounds 
 * 
 * @author  Harry Hochheiser &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:hsh@nih.gov">hsh@nih.gov</a>
 *
 * @version 2.2
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 */


public class BufferedCanvas extends PCanvas implements BufferedObject {

	/**
	 * 
	 * @return canvas bounds with appropriate buffers for centering
	 */	
	public PBounds getBufferedBounds() {
		PBounds b = getLayer().getFullBounds();
		return new PBounds(b.getX()-Constants.BORDER,
			b.getY()-Constants.BORDER,b.getWidth()+2*Constants.BORDER,
			b.getHeight()+2*Constants.BORDER); 
	}	
}