/*
 * org.openmicroscopy.shoola.env.data.model.ChainExecutionData
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

package org.openmicroscopy.shoola.env.data.model;

//Java imports
import java.util.List;
import java.util.Date;

//Third-party libraries

//Application-internal dependencies

/** 
 * An analysis chain execution object
 * 
 * @author  Harry Hochheiser &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:hsh@nih.gov">hsh@nih.gov</a>
 *
 *
 * @version 2.2 
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 * @since OME2.2
 */
public class ChainExecutionData implements DataObject, Comparable
{

	private int id;
	private AnalysisChainData chain;
	private DatasetData dataset;
	private List nodeExecutions;
	private String timestamp;
	private Date date;
	
	public ChainExecutionData() {}
	
	public ChainExecutionData(int id,AnalysisChainData chain,
			DatasetData dataset,List nodeExecutions,String timestamp) 
	{	
		this.id = id;
		this.chain = chain;
		this.dataset=dataset;
		this.nodeExecutions = nodeExecutions;
		this.timestamp = timestamp;
	}
	
	/** Required by the DataObject interface. */
	public DataObject makeNew() { return new ChainExecutionData(); }
	
	public AnalysisChainData getChain() {
		return chain;
	}
	public DatasetData getDataset() {
		return dataset;
	}
	
	public int getID() {
		return id;
	}

	public List getNodeExecutions() {
		return nodeExecutions;
	}
	
	public String getTimestamp() {
		return timestamp;
	}
	
	public Date getDate() {
		return date;
	}
	
	public void setChain(AnalysisChainData data) {
		chain = data;
	}

	public void setDataset(DatasetData dataset) {
		this.dataset = dataset;
	}
	
	public void setID(int i) {
		id = i;
	}
	
	public void setNodeExecutions(List nodeExecutions) {
		this.nodeExecutions = nodeExecutions;
	}
	
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
	
	public void setDate(Date date) {
		this.date =date;
	}
	
	// executions can be ordered by date
	public int compareTo(Object o) throws ClassCastException {
		if (! (o instanceof ChainExecutionData))
			throw new ClassCastException();
		ChainExecutionData otherExec = (ChainExecutionData) o;
		if (date == null)
			return -1;
		if (otherExec.getDate() == null)
			return -1;
		
		return date.compareTo(otherExec.getDate());
	}
}
