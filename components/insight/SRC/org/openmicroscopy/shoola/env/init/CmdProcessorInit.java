/*
 * org.openmicroscopy.shoola.env.init.CmdProcessorInit
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006 University of Dundee. All rights reserved.
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

package org.openmicroscopy.shoola.env.init;


//Java imports

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.env.LookupNames;
import org.openmicroscopy.shoola.env.config.Registry;
import org.openmicroscopy.shoola.util.concur.tasks.AsyncProcessor;
import org.openmicroscopy.shoola.util.concur.tasks.CmdProcessor;

/** 
 * This task intializes the {@link CmdProcessor} the container will use
 * for asyncrhonous operations.
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
public final class CmdProcessorInit
    extends InitializationTask
{
    
    /** Constructor required by superclass. */
    CmdProcessorInit() {}

    /**
     * Returns the name of this task.
     * @see InitializationTask#getName()
     */
    String getName() { return "Initializing Command Processor"; }

    /** 
     * Does nothing, as this task requires no set up.
     * @see InitializationTask#configure()
     */
    void configure() {}

    /**
     * Carries out this task.
     * @see InitializationTask#execute()
     */
    void execute()
    {
        //TODO: What processor to use should be externalized in cfg.
        CmdProcessor processor = new AsyncProcessor();
        Registry reg = container.getRegistry();
        reg.bind(LookupNames.CMD_PROCESSOR, processor);
    }

    /** 
     * Does nothing.
     * @see InitializationTask#rollback()
     */
    void rollback() {}

}
