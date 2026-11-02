package com.ashkan.hotel;

import java.sql.*;
import java.util.*;

/** Append-only audit trail for payment-domain state transitions. */
public final class FinancialAuditRepository extends JdbcDomainRepository {
    public FinancialAuditRepository(String url,String user,String password,int poolSize){super(url,user,password,poolSize);}
    public void append(String actor,String action,String resourceId,String details)throws SQLException {
        withConnection(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO financial_audit_events(actor,action,resource_id,details) VALUES(?,?,?,?)")){
            p.setString(1,actor);p.setString(2,action);p.setString(3,resourceId);p.setString(4,details);p.executeUpdate();}return null;});
    }
}
