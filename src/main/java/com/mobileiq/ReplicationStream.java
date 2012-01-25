package com.mobileiq;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public final class ReplicationStream extends ObjectInputStream{
  private ClassLoader classLoader = null;

  public ReplicationStream(InputStream stream, ClassLoader classLoader)throws IOException{
    super(stream);
    this.classLoader = classLoader;
  }

  public Class resolveClass(ObjectStreamClass classDesc)throws ClassNotFoundException, IOException{
    try{
      return this.classLoader.loadClass(classDesc.getName());
    }catch (Exception x) {
    }
    return getClass().getClassLoader().loadClass(classDesc.getName());
  }
}