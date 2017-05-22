/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.util.List;
import java.util.Map;

public class QueryRestrictor {
    public static final String MATCH_NO_DOCUMENTS_QUERY = "this_field_will_never_exist:false";

    public static String restrict(String query, String userId, List<String> groupIds, AuthConfig authConfig, Map<String, AuthConfig> remoteAuthConfigs, boolean excludeVersions) {
        if ("*".equals(query.trim())) query = "*:*";
        if (excludeVersions) {
            query = "(" + query + ") AND NOT objatt_isVersion:true"; 
        }
        
        if ("admin".equals(userId)) return query;
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(query).append(")");
        sb.append(" AND (");
        sb.append("aclRead:public OR aclWrite:public");
        if (userId != null) {
            sb.append(" OR ((aclRead:self OR aclWrite:self) AND ");
            appendIsUserOrGroup(sb, "id", userId, groupIds);
            sb.append(")");
            sb.append(" OR ((aclRead:creator OR aclWrite:creator) AND ");
            appendIsUserOrGroup(sb, "createdBy", userId, groupIds);
            sb.append(")");
            sb.append(" OR ");
            appendIsUserOrGroup(sb, "aclRead", userId, groupIds);
            sb.append(" OR ");
            appendIsUserOrGroup(sb, "aclWrite", userId, groupIds);
            sb.append(" OR aclRead:authenticated OR aclWrite:authenticated");
        }
        sb.append(" OR (aclRead:missing AND ");
        appendRestrictionsForAllAuthConfigs(sb, userId, groupIds, authConfig, remoteAuthConfigs, false);
        sb.append(")");
        sb.append(" OR (aclWrite:missing AND ");
        appendRestrictionsForAllAuthConfigs(sb, userId, groupIds, authConfig, remoteAuthConfigs, true);
        sb.append(")");
        sb.append(")");
        return sb.toString();
    }

    private static void appendRestrictionsForAllAuthConfigs(StringBuilder sb, String userId, List<String> groupIds, AuthConfig authConfig, Map<String, AuthConfig> remoteAuthConfigs, boolean isWrite) {
        sb.append("(");
        boolean first = true;
        if (remoteAuthConfigs != null) {
            for (Map.Entry<String, AuthConfig> entry : remoteAuthConfigs.entrySet()) {
                String remoteRepository = entry.getKey();
                AuthConfig remoteAuthConfig = entry.getValue();
                if (!first) sb.append(" OR ");
                first = false;
                sb.append("(remoteRepository:").append(escape(remoteRepository)).append(" AND ");
                appendRestrictionsForAuthConfig(sb, userId, groupIds, remoteAuthConfig, isWrite);
                sb.append(")");
            }
        }
        if (!first) sb.append(" OR ");
        sb.append("(remoteRepository:null AND ");
        appendRestrictionsForAuthConfig(sb, userId, groupIds, authConfig, isWrite);
        sb.append("))");
    }

    private static void appendRestrictionsForAuthConfig(StringBuilder sb, String userId, List<String> groupIds, AuthConfig authConfig, boolean isWrite) {
        sb.append("(");
        boolean first = true;
        StringBuilder isDefaultTypeSb = new StringBuilder("(*:*");
        for (Map.Entry<String, DefaultAcls> entry : authConfig.schemaAcls.entrySet()) {
            String objectType = entry.getKey();
            isDefaultTypeSb.append(" -type:").append(escape(objectType));
            DefaultAcls aclsForType = entry.getValue();
            if (!first) sb.append(" OR ");
            first = false;
            sb.append("(type:").append(escape(objectType)).append(" AND ");
            appendDefaultAclRestrictions(sb, aclsForType, userId, groupIds, isWrite);
            sb.append(")");
        }
        isDefaultTypeSb.append(")");
        if (!first) {
            sb.append(" OR (");
            sb.append(isDefaultTypeSb);
            sb.append(" AND ");
        }
        appendDefaultAclRestrictions(sb, authConfig.defaultAcls, userId, groupIds, isWrite);
        if (!first) {
            sb.append(")");
        }
        sb.append(")");
    }
    
    private static void appendDefaultAclRestrictions(StringBuilder sb, DefaultAcls defaultAcls, String userId, List<String> groupIds, boolean isWrite) {
        sb.append("(").append(MATCH_NO_DOCUMENTS_QUERY);
        List<String> acl = isWrite ? defaultAcls.defaultAclWrite : defaultAcls.defaultAclRead;
        for (String permittedId : acl) {
            if ("public".equals(permittedId)) sb.append(" OR *:*");
            else if (userId != null) {
                if ("authenticated".equals(permittedId)) sb.append(" OR *:*");
                else if ("self".equals(permittedId)) {
                    sb.append(" OR ");
                    appendIsUserOrGroup(sb, "id", userId, groupIds);
                } else if ("creator".equals(permittedId)) {
                    sb.append(" OR ");
                    appendIsUserOrGroup(sb, "createdBy", userId, groupIds);
                } else if (userId.equals(permittedId) || groupIds.contains(permittedId)) {
                    sb.append(" OR *:*");
                }
            }
        }
        sb.append(")");
    }
    
    private static void appendIsUserOrGroup(StringBuilder sb, String field, String userId, List<String> groupIds) {
        sb.append("(");
        sb.append(field).append(":").append(escape(userId));
        for (String groupId : groupIds) {
            sb.append(" OR ");
            sb.append(field).append(":").append(escape(groupId));
        }
        sb.append(")");
    }

    /**
     * Returns a String where those characters that QueryParser
     * expects to be escaped are escaped by a preceding <code>\</code>.
     */
    public static String escape(String s) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        // These characters are part of the query syntax and must be escaped
        if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
          || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
          || c == '*' || c == '?' || c == '|' || c == '&' || c == '/') {
          sb.append('\\');
        }
        sb.append(c);
      }
      return sb.toString();
    }
}
