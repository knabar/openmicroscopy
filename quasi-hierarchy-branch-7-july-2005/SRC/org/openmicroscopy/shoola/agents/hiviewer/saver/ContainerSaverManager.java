/*
 * org.openmicroscopy.shoola.agents.hiviewer.saver.ContainerSaverManager
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

package org.openmicroscopy.shoola.agents.hiviewer.saver;



//Java imports

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.hiviewer.IconManager;
import org.openmicroscopy.shoola.util.ui.UIUtilities;

/** 
 * Manager of the {@link ContainerSaver}.
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
class ContainerSaverManager
{
    
    private ContainerSaver      model; 
    
    private Preview             preview;
    
    /** Brings up the selection dialog. */
    void showSelectionDialog()
    {
        IconManager im = IconManager.getInstance();
        SelectionDialog dialog = new SelectionDialog(model,
                                    im.getIcon(IconManager.QUESTION));
        dialog.pack();
        UIUtilities.centerAndShow(dialog);
    }
    
    /** Brings up the preview widget. */
    void showPreview()
    {
        //Hide the model
        model.setVisible(false);
        if (preview == null) preview = new Preview(model);
        preview.pack();
        UIUtilities.centerAndShow(preview);
    }
    
    public ContainerSaverManager(ContainerSaver model)
    {
        this.model = model;
    }

}
