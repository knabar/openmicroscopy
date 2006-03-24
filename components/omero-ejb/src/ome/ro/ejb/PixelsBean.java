/* ome.ro.ejb.PixelsBean
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2005 Open Microscopy Environment
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

package ome.ro.ejb;

//Java imports
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.PreDestroy;
import javax.ejb.Remote;
import javax.ejb.Stateless;

import org.jboss.annotation.security.SecurityDomain;

//Third-party imports

//Application-internal dependencies
import ome.api.IPixels;
import ome.model.core.Pixels;
import ome.model.display.RenderingDef;

@Stateless
@Remote(IPixels.class)
@Local(IPixels.class)
@SecurityDomain("OmeroSecurity")
public class PixelsBean extends AbstractBean implements IPixels
{

    IPixels delegate;
    
    public PixelsBean(){
        super();
        delegate = (IPixels) applicationContext.getBean("pixelsService");
    }
    
    @PreDestroy
    public void destroy()
    {
        delegate = null;
        super.destroy();
    }

    // ~ DELEGATION
    // =========================================================================
    
    @RolesAllowed("user") 
    public Pixels retrievePixDescription(long pixId)
    {
        return delegate.retrievePixDescription(pixId);
    }

    @RolesAllowed("user") 
    public RenderingDef retrieveRndSettings(long pixId)
    {
        return delegate.retrieveRndSettings(pixId);
    }

    @RolesAllowed("user") 
    public void saveRndSettings(RenderingDef rndSettings)
    {
        delegate.saveRndSettings(rndSettings);
    }
    
    
}
