package com.mobileiq;

public class ReplicationConfig{
	
  protected static ReplicationConfig inst = null;

  protected boolean sync_session_creation = false;

  protected long session_creation_timeout = 5000L;

  protected boolean sync_session_deletion = false;

  protected long session_deletion_timeout = 5000L;

  protected long session_state_transfer_timeout = 10000L;

  protected boolean sync_attribute_update = false;

  protected long attribute_update_timeout = 5000L;

  protected boolean sync_attribute_remove = false;

  protected long attribute_removal_timeout = 5000L;

  protected boolean sync_set_principal = false;

  protected long set_principal_timeout = 5000L;

  public ReplicationConfig getInstance()throws Exception{
    if (inst != null) {
      return inst;
    }
    return ReplicationConfig.inst = createInstance();
  }

  public boolean getSyncSessionCreation() {
    return this.sync_session_creation; } 
  public long getSessionCreationTimeout() { return this.session_creation_timeout; } 
  public boolean getSyncSessionDeletion() {
    return this.sync_session_deletion; } 
  public long getSessionDeletionTimeout() { return this.session_deletion_timeout; } 
  public long getSessionStateTransferTimeout() {
    return this.session_state_transfer_timeout;
  }
  public boolean getSyncAttributeUpdate() { return this.sync_attribute_update; } 
  public long getAttributeUpdateTimeout() { return this.attribute_update_timeout; } 
  public boolean getSyncAttributeRemove() {
    return this.sync_attribute_remove; } 
  public long getAttributeRemovalTimeout() { return this.attribute_removal_timeout; } 
  public boolean getSyncPrincipalSet() {
    return this.sync_set_principal; } 
  public long getPrincipalSetTimeout() { return this.set_principal_timeout;
  }

  protected ReplicationConfig createInstance() throws Exception{
    ReplicationConfig retval = new ReplicationConfig();

    String val = System.getProperty("sync_session_creation");
    if (val != null) {
      retval.sync_session_creation = new Boolean(val).booleanValue();
    }
    val = System.getProperty("session_creation_timeout");
    if (val != null) {
      retval.session_creation_timeout = Long.parseLong(val);
    }

    return retval;
  }
}