/*
 * Copyright (C) 2005-2009 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.domain.audit;

import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.repo.audit.AuditState;
import org.alfresco.service.cmr.audit.AuditInfo;
import org.alfresco.service.cmr.audit.AuditService.AuditQueryCallback;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.Pair;

/**
 * DAO services for <b>alf_audit_XXX</b> tables.
 * <p>
 * The older methods are supported by a different implementation and will eventually
 * be deprecated and phased out.
 * 
 * @author Derek Hulley
 * @since 3.2
 */
public interface AuditDAO
{
    /**
     * Create an audit entry.
     * 
     * @param auditInfo
     * @since 2.1
     */
    public void audit(AuditState auditInfo);

    /**
     * Get the audit trail for a node.
     * 
     * @since 2.1
     */
    public List<AuditInfo> getAuditTrail(NodeRef nodeRef);
    
    /*
     * V3.2 methods after here only, please
     */

    /**
     * Information about the audit application to be passed in an out of the interface.
     * 
     * @author Derek Hulley
     * @since 3.2
     */
    public static class AuditApplicationInfo
    {
        private Long id;
        private String name;
        private Long modelId;
        private Set<String> disabledPaths;
        
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("AuditApplicationInfo ")
              .append("[ id=").append(id)
              .append(", name=").append(name)
              .append(", modelId=").append(modelId)
              .append(", disabledPaths=").append(disabledPaths)
              .append("]");
            return sb.toString();
        }
        
        public Long getId()
        {
            return id;
        }
        public void setId(Long id)
        {
            this.id = id;
        }
        public String getName()
        {
            return name;
        }
        public void setname(String name)
        {
            this.name = name;
        }
        public Long getModelId()
        {
            return modelId;
        }
        public void setModelId(Long modelId)
        {
            this.modelId = modelId;
        }
        public Set<String> getDisabledPaths()
        {
            return disabledPaths;
        }
        public void setDisabledPaths(Set<String> disabledPaths)
        {
            this.disabledPaths = disabledPaths;
        }
    }
    
    /**
     * Creates a new audit model entry or finds an existing one
     * 
     * @param               the URL of the configuration
     * @return              Returns the ID of the config matching the input stream and the
     *                      content storage details
     */
    Pair<Long, ContentData> getOrCreateAuditModel(URL url);
    
    /**
     * Get the audit application details.
     * 
     * @param applicationName   the name of the application
     * @return                  Returns details of an existing application or <tt>null</tt> if it doesn't exist
     */
    AuditApplicationInfo getAuditApplication(String applicationName);

    /**
     * Creates a new audit application.  The application name must be unique.
     * 
     * @param application       the name of the application
     * @param modelId           the ID of the model configuration
     */
    AuditApplicationInfo createAuditApplication(String application, Long modelId);
    
    /**
     * Update the audit application to refer to a new model.
     * If the model did not change, then nothing will be done.
     * 
     * @param id                the ID of the audit application
     * @param modelId           the ID of the new model
     */
    void updateAuditApplicationModel(Long id, Long modelId);
    
    /**
     * Update the audit application to hold a new set of disabled paths.
     * If the value did not change, then nothing will be done.
     * 
     * @param id                the ID of the audit application
     * @param disabledPaths     the new disabled paths
     */
    void updateAuditApplicationDisabledPaths(Long id, Set<String> disabledPaths);
    
    /**
     * Create a new audit entry with the given map of values.
     * 
     * @param applicationId an existing audit application ID
     * @param time          the time (ms since epoch) to log the entry against
     * @param username      the authenticated user (<tt>null</tt> if not present)
     * @param values        the values to record
     * @return              Returns the unique entry ID
     */
    Long createAuditEntry(Long applicationId, long time, String username, Map<String, Serializable> values);
    
    void findAuditEntries(
            AuditQueryCallback callback,
            String applicationName, String user, Long from, Long to, int maxResults);
    
    void findAuditEntries(
            AuditQueryCallback callback,
            String applicationName, String user, Long from, Long to,
            String searchKey, String searchString,
            int maxResults);
}