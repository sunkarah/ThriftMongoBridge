/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.breizhbeans.thrift.tools.thriftmongobridge.protocol;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Hex;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TSet;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.breizhbeans.thrift.tools.thriftmongobridge.secured.TBSONSecuredWrapper;

public class TBSONProtocol extends TProtocol {
	public static final char QUOTE = '"';

	private static ThreadLocal<Stack<Context>> threadSafeContextStack = new ThreadLocal<>();
	private static ThreadLocal<DBObject> threadSafeDBObject = new ThreadLocal<>();
  private static ThreadLocal<Map<Class<?>,List<Short>>> threadSafeFieldIds = new ThreadLocal<>();
	private static ThreadLocal<TBase<?, ?>> threadSafeTBase = new ThreadLocal<>();

  private static ThreadLocal<Map<Class<?>,Map<String, ThriftField>>> threadSafeTFields = new ThreadLocal<>();

  private static TBSONSecuredWrapper tbsonSecuredWrapper = new DefaultUnsecuredWrapper();

  public static void addSecuredWrapper(TBSONSecuredWrapper tbsonSecuredWrapper) {
    TBSONProtocol.tbsonSecuredWrapper = tbsonSecuredWrapper;
  }

  public static TBSONSecuredWrapper getSecuredWrapper() {
    return TBSONProtocol.tbsonSecuredWrapper;
  }

  /**
	 * Factory
	 */
	public static class Factory implements TProtocolFactory {
		public TProtocol getProtocol(TTransport trans) {
			return new TBSONProtocol();
		}
	}

	/**
	 * Constructor
	 */
	public TBSONProtocol() {
		super(null);
	}

	public DBObject getDBObject() {
		return threadSafeDBObject.get();
	}

	public void setDBOject(DBObject dbObject) {
		threadSafeDBObject.set(dbObject);
	}

  public void setFieldIdsFilter(TBase<?, ?> base, TFieldIdEnum[] fieldIds) {
    base.getClass();
    List<Short> filteredFields = new ArrayList<>();

    for(TFieldIdEnum tFieldIdEnum : fieldIds){
      filteredFields.add(tFieldIdEnum.getThriftFieldId());
    }
    Map<Class<?>,List<Short>> filter = new HashMap<>();
    filter.put(base.getClass(), filteredFields);
    threadSafeFieldIds.set(filter);
  }

  public void setBaseObject(TBase<?, ?> base) {
		threadSafeTBase.set(base);
	}

	private static final TStruct ANONYMOUS_STRUCT = new TStruct();
	private static final TField ANONYMOUS_FIELD = new TField();
	private static final TMessage EMPTY_MESSAGE = new TMessage();
	private static final TSet EMPTY_SET = new TSet();
	private static final TList EMPTY_LIST = new TList();
	private static final TMap EMPTY_MAP = new TMap();


  protected class ThriftField {
    public TFieldIdEnum tfieldIdEnum;
    public org.apache.thrift.meta_data.FieldMetaData fieldMetaData;

    public ThriftField(TFieldIdEnum tfieldIdEnum, org.apache.thrift.meta_data.FieldMetaData fieldMetaData) {
      this.tfieldIdEnum = tfieldIdEnum;
      this.fieldMetaData = fieldMetaData;
    }
  }

	protected abstract class Context {
		public DBObject dbObject = null;
    public DBObject securedDbObject = null;
		public Object thriftObject;
    public boolean secured = false;
    public boolean hash = false;
    public String name = null;
    public Short thriftId;


        abstract void addSecured(String value);

        abstract void add(String value);

        abstract void add(int value);

        abstract void add(long value);

        abstract void add(double value);

        abstract void add(ByteBuffer bin);



		public void addDBObject(DBObject dbObject) {
			this.dbObject = dbObject;

		}
	}

	protected class FieldContext extends Context {

    public FieldContext(String name, short thriftId, TBSONSecuredWrapper.ThriftSecuredField securedField) {
      this.name = name;
      this.thriftId = thriftId;
      this.secured = securedField.isSecured();
      this.hash = securedField.isHash();
    }


		public String name;
		public Object value;
    public Object securedValue;


    @Override
    void addSecured(String value) {
      securedValue = value;
    }

    void add(String value) {
			this.value = value;
		}

		void add(int value) {
			this.value = value;
		}

		void add(long value) {
			this.value = value;
		}

		void add(double value) {
			this.value = value;
		}

		public void add(ByteBuffer bin) {
			this.value = bin.array();
		}
	}

	// Class for all object container likes list set and maps
	protected abstract class ObjectContainerContext extends Context {
        abstract void add(DBObject value);
	}

	protected class ListContext extends ObjectContainerContext {
		BasicDBList dbList = new BasicDBList();
		Integer index = 0;

    @Override
    void addSecured(String value) {

    }

    void add(String value) {
			dbList.put(index.toString(), value);
			index++;
		}

        void add(int value) {

            dbList.put(index.toString(), Integer.valueOf(value));
            index++;
        }

        @Override
        void add(long value) {
            dbList.put(index.toString(), Long.valueOf(value));
            index++;
        }

        void add(double value) {
            dbList.put(index.toString(), Double.valueOf(value));
            index++;
        }

        @Override
        void add(ByteBuffer bin) {
            dbList.put(index.toString(), bin.array());
            index++;
        }

        void add(DBObject value) {
			dbList.put(index.toString(), value);
			index++;
		}


		Object next() {
			Object object = dbList.get(index);
			index++;
			return object;
		}
	}

	protected class MapContext extends ObjectContainerContext {
		private DBObject dbMap = new BasicDBObject();
    private byte keyType;

		public Stack<String> keyStack;
		boolean extractKey = true;

    public MapContext(byte keyType) {
        this.keyType = keyType;
    }

		public void setDbMap( DBObject dbMap ) {
			this.dbMap = dbMap;
			this.keyStack = new Stack<>();
			this.keyStack.addAll(this.dbMap.keySet());			
		}
		
		// A Map
		private String stringKey = null;

    @Override
    void addSecured(String value) {

    }

    void add(String value) {
			if (stringKey != null) {
				dbMap.put(stringKey, value);
				stringKey = null;
			} else {
				stringKey = value;
			}
		}

    @Override
    void add(int value) {
        if (stringKey != null) {
            dbMap.put(stringKey,value);
            stringKey = null;
        } else {
            stringKey = Integer.toString(value);
        }
    }

    @Override
    void add(long value) {
        if (stringKey != null) {
            dbMap.put(stringKey,value);
            stringKey = null;
        } else {
            stringKey = Long.toString(value);
        }
    }

    @Override
    void add(double value) {
        if (stringKey != null) {
            dbMap.put(stringKey,value);
            stringKey = null;
        } else {
            stringKey = Double.toString(value);
        }
    }

    @Override
    void add(ByteBuffer bin) {
        if (stringKey != null) {
            dbMap.put(stringKey, bin.array());
            stringKey = null;
        }
    }

    void add(DBObject value) {
			if (stringKey != null) {
				dbMap.put(stringKey, value);
				stringKey = null;
			}
		}
		
		Object next() {
			if( extractKey ) {
				extractKey = false;
				return this.keyStack.firstElement();
			} else {
				extractKey = true;
				String key = this.keyStack.remove(0);
				return dbMap.get(key);
			}
		}


    public boolean isNextKey() {
            return  extractKey;
        }
	}

	protected class StructContext extends Context {
		private Stack<String> fieldsStack;
		public Object thriftObject;

		public void setDbObject( DBObject dbObject) {
			this.dbObject = dbObject;
			this.fieldsStack = new Stack<String>();
			this.fieldsStack.addAll(this.dbObject.keySet());
		}
		
		public StructContext(String structName) {
      name = structName;
			dbObject = new BasicDBObject();
      securedDbObject = null;
		}

    @Override
    void addSecured(String value) {

    }

    @Override
		void add(String value) {
		}

    @Override
    void add(int value) {
        // Nothing to do
    }

    @Override
    void add(long value) {
        // Nothing to do
    }

    @Override
    void add(double value) {
        // Nothing to do
    }

    @Override
    void add(ByteBuffer bin) {
        // Nothing to do
    }
  }

	/**
	 * Push a new write context onto the stack.
	 */
	protected void pushContext(Context c) {
		Stack<Context> stack = threadSafeContextStack.get();
		if (stack == null) {
			stack = new Stack<Context>();
			stack.push(c);
			threadSafeContextStack.set(stack);
		} else {
			threadSafeContextStack.get().push(c);
		}
	}

	protected boolean isContextEmpty() {
		Stack<Context> stack = threadSafeContextStack.get();
		if(stack==null || stack.size()==0) {
			return true;
		}
		return false;
	}

	/**
	 * Pop the last write context off the stack
	 */
	protected Context popContext() {
		return threadSafeContextStack.get().pop();
	}

	protected Context peekContext() {
		return threadSafeContextStack.get().peek();
	}

	public void writeMessageBegin(TMessage message) throws TException {
		// trans_.write(LBRACKET);
		pushContext(new ListContext());
	}

	public void writeMessageEnd() throws TException {
		popContext();
	}

	public void writeStructBegin(TStruct struct) throws TException {
		StructContext c = new StructContext(struct.name);
		pushContext(c);
	}

	public void writeStructEnd() throws TException {
		// Gets the struct
    Context ctx = popContext();
		DBObject dbObject = ctx.dbObject;

    if(ctx.securedDbObject != null) {
      dbObject.put("securedwrap", ctx.securedDbObject);
    }

    // Sets the DBObject for output
		threadSafeDBObject.set(dbObject);

		// if the stack is not empty add the struct current stack field
		if(!isContextEmpty()) {
			Context fieldContext = peekContext();

			// For the ListContext adds the strcut to the context
			if (fieldContext instanceof ObjectContainerContext) {
                ((ObjectContainerContext)fieldContext).add(dbObject);
			} else {
				// Thrift general field adds the object to the field
				fieldContext.addDBObject(dbObject);
			}
		}
	}

	public void writeFieldBegin(TField field) throws TException {
		// Note that extra type information is omitted in BSON!
    Context ctx = peekContext();
    //TBSONSecuredWrapper.ThriftSecuredField securedField=tbsonSecuredWrapper.getField(ctx.name, field.id);
    TBSONSecuredWrapper.ThriftSecuredField securedField=tbsonSecuredWrapper.getField(null, field.id);

    if( securedField.isSecured() && ctx.securedDbObject==null){
      ctx.securedDbObject = new BasicDBObject();
    }

		pushContext(new FieldContext(field.name, field.id, securedField));
	}

	public void writeFieldEnd() throws TException {
		Context c = popContext();
		Context dbObjectContext = peekContext();
		if (c.dbObject == null) {
			dbObjectContext.dbObject.put(((FieldContext) c).name, ((FieldContext) c).value);
      if( dbObjectContext.securedDbObject!=null) {
        dbObjectContext.securedDbObject.put(((FieldContext) c).thriftId.toString(), ((FieldContext) c).securedValue);
      }
		} else {
			dbObjectContext.dbObject.put(((FieldContext) c).name, c.dbObject);
		}
	}

	public void writeFieldStop() {
	}

	public void writeMapBegin(TMap map) throws TException {
		MapContext c = new MapContext(map.keyType);
		pushContext(c);
	}

	public void writeMapEnd() throws TException {
		// Gets the map
		MapContext map = (MapContext) popContext();
		// Add the map to the current field
		Context fieldContext = peekContext();
		fieldContext.addDBObject(map.dbMap);
	}

	public void writeListBegin(TList list) throws TException {
		pushContext(new ListContext());
	}

	public void writeListEnd() throws TException {
		// Gets the list
		ListContext list = (ListContext) popContext();
		// Add the list to the current field
		Context fieldContext = peekContext();
		fieldContext.addDBObject(list.dbList);
	}

	/**
	 * A Set have the same serialization of a thrift List
	 */
	public void writeSetBegin(TSet set) throws TException {
		pushContext(new ListContext());
	}

	/**
	 * A Set have the same serialization of a thrift List
	 */
	public void writeSetEnd() throws TException {
		// Gets the list
		ListContext list = (ListContext) popContext();
		// Add the list to the current field
		Context fieldContext = peekContext();
		fieldContext.addDBObject(list.dbList);
	}

	public void writeBool(boolean b) throws TException {
		writeByte(b ? (byte) 1 : (byte) 0);
	}

	public void writeByte(byte b) throws TException {
		peekContext().add((int) b);
	}

	public void writeI16(short i16) throws TException {
		peekContext().add((int) i16);
	}

	public void writeI32(int i32) throws TException {
		peekContext().add(i32);
	}

	public void writeI64(long i64) throws TException {
		peekContext().add(i64);
	}

	public void writeDouble(double dub) throws TException {
		peekContext().add(dub);
	}

	public void writeString(String str) throws TException {
    try {
      Context context = peekContext();

      if(context.secured){
        // compute hash from the value if needed
        if(context.hash) {
          context.add(new Long(TBSONProtocol.tbsonSecuredWrapper.digest64(str.getBytes("UTF-8"))));
        }
        // crypt the value and add it to the secured field
        context.addSecured(Hex.encodeHexString(TBSONProtocol.tbsonSecuredWrapper.cipher(str.getBytes("UTF-8"))));
      } else {
        context.add(new String(str.getBytes("UTF-8")));
      }
    } catch (UnsupportedEncodingException uex) {
      throw new TException("JVM DOES NOT SUPPORT UTF-8");
    }
	}

	public void writeBinary(ByteBuffer bin) throws TException {
		peekContext().add(bin);
	}

	/**
	 * Reading methods.
	 */

	public TMessage readMessageBegin() throws TException {
		return EMPTY_MESSAGE;
	}

	public void readMessageEnd() {
	}

	public TStruct readStructBegin() throws TException {
		try {
			DBObject dbObject = null;
			Object thriftObject = null;

			if(!isContextEmpty()) {
				Context currentContext = peekContext();

				if (currentContext instanceof ListContext) {
					dbObject = (DBObject) ((ListContext) currentContext).next();
					thriftObject = ((ListContext) peekContext()).thriftObject;
				} else if (currentContext instanceof MapContext) {
					dbObject = (DBObject) ((MapContext) currentContext).next();
					thriftObject = ((MapContext) peekContext()).thriftObject;
				} else {
					return ANONYMOUS_STRUCT;
				}
			} else {
				thriftObject = threadSafeTBase.get();
				dbObject = getDBObject();
			}
      StructContext context = new StructContext(thriftObject.getClass().getSimpleName());
			context.setDbObject(dbObject);
			context.thriftObject = thriftObject;
			pushContext(context);

			return ANONYMOUS_STRUCT;
		} catch (Exception exp) {
      throw new TException("Unexpected readStructBegin", exp);
		}
	}

	public void readStructEnd() {
		popContext();
	}

	public TField readFieldBegin() throws TException {
		StructContext context = (StructContext) peekContext();
		if(context.fieldsStack.isEmpty()) {
      // Empty stack -> returns a TType.STOP
      return new TField();
    }
    String fieldName = context.fieldsStack.peek();

    TField currentField = getTField(context.thriftObject, fieldName);
    //currentField.id
    // IF the field is skiped change the type to void
    Map<Class<?>, List<Short>> filter = threadSafeFieldIds.get();

    if(filter!=null) {
      List<Short> fieldsFiltered =  filter.get(context.thriftObject.getClass());
      if(fieldsFiltered != null && fieldsFiltered.contains(currentField.id)) {
        return new TField(currentField.name, TType.VOID, currentField.id);
      }
    }

    // If the field is a struct push a struct context in the stack
    if (currentField.type == TType.STRUCT) {
      StructContext structContext = new StructContext(fieldName);
      structContext.setDbObject((DBObject) context.dbObject.get(fieldName));
      structContext.thriftObject = getThriftObject(context.thriftObject, fieldName);
      pushContext(structContext);
    }
    return currentField;
	}

	private Object getThriftObject(Object thriftObject, String fieldName) throws TException {
		try {
      Map<String, ThriftField> classFields = getClassFields(thriftObject);
      ThriftField thriftField = classFields.get(fieldName);

      if(thriftField!=null) {
        switch (thriftField.fieldMetaData.valueMetaData.type) {
          case TType.LIST:
            ListMetaData listMetaData = (ListMetaData) thriftField.fieldMetaData.valueMetaData;
            if( listMetaData.elemMetaData.isStruct()) {
              return ((StructMetaData) listMetaData.elemMetaData).structClass.newInstance();
            }
            return null;
          case TType.SET:
            SetMetaData setMetaData = (SetMetaData) thriftField.fieldMetaData.valueMetaData;
            if( setMetaData.isStruct()) {
              return ((StructMetaData) setMetaData.elemMetaData).structClass.newInstance();
            }
            return null;
          case TType.MAP:
            MapMetaData mapMetaData = (MapMetaData) thriftField.fieldMetaData.valueMetaData;
            if( mapMetaData.valueMetaData.isStruct()) {
              return ((StructMetaData) mapMetaData.valueMetaData).structClass.newInstance();
            }
            return null;
          case TType.STRUCT:
            return ((StructMetaData) thriftField.fieldMetaData.valueMetaData).structClass.newInstance();
        }
      }
			throw new Exception("FieldName not finded name=" + fieldName);
		} catch (Exception exp) {
      throw new TException("Unexpected getListThriftObject fieldName=" + fieldName, exp);
		}
	}


  private Map<String, ThriftField> getClassFields(Object thriftObject) throws TException {
    try {
      Map<Class<?>,Map<String, ThriftField>> thriftFields = threadSafeTFields.get();

      if(thriftFields==null){
        thriftFields = new HashMap<>();
      }

      Class<?> tbase = thriftObject.getClass();
      Map<String, ThriftField> classTFields = thriftFields.get(tbase);

      if(classTFields!=null){
        return classTFields;
      }

      classTFields = new HashMap<>();

      Field metafaField = thriftObject.getClass().getField("metaDataMap");
      Map<?, org.apache.thrift.meta_data.FieldMetaData> fields = (Map<?, org.apache.thrift.meta_data.FieldMetaData>) metafaField.get(thriftObject);
      // recurse on all sub structures
      for (Entry<?, org.apache.thrift.meta_data.FieldMetaData> entry : fields.entrySet()) {
        TFieldIdEnum field = (TFieldIdEnum) entry.getKey();
        classTFields.put(field.getFieldName(), new ThriftField(field, entry.getValue()));
      }

      thriftFields.put(tbase, classTFields);

      threadSafeTFields.set(thriftFields);
      return classTFields;
    } catch (Exception exp) {
      throw new TException("Unexpected object", exp);
    }
  }


	private TField getTField(Object thriftObject, String fieldName) throws TException {
		try {
      Map<String, ThriftField> classFields = getClassFields(thriftObject);
      ThriftField thriftField = classFields.get(fieldName);

      if(thriftField==null) {
        // Empty field -> skip
        return new TField();
      }

      byte type = thriftField.fieldMetaData.valueMetaData.type;
      short id = thriftField.tfieldIdEnum.getThriftFieldId();

      // An enum type is deserialized as an I32
      if (TType.ENUM == type) {
        type = TType.I32;
      }

      return new TField("", type, id);
    } catch (Exception exp) {
			throw new TException("Unexpected getTField fieldName=" + fieldName, exp);
		}
	}

	public void readFieldEnd() {
		StructContext context = (StructContext) peekContext();
		if(!context.fieldsStack.isEmpty()) {
			context.fieldsStack.pop();
		}
	}

	public TMap readMapBegin() throws TException {
		StructContext context = (StructContext) peekContext();
		if(context.fieldsStack.isEmpty()) {
      return EMPTY_MAP;
    }
    String fieldName = context.fieldsStack.peek();

    MapContext mapContext = new MapContext(TType.VOID);
    BasicDBObject dbMap = (BasicDBObject) context.dbObject.get(fieldName);

    mapContext.setDbMap(dbMap);
    mapContext.thriftObject = getThriftObject(context.thriftObject, fieldName);
    pushContext(mapContext);
    return new TMap(TType.STRING, TType.STRING,dbMap.size());
	}

	public void readMapEnd() {
		popContext();		
	}

	public TList readListBegin() throws TException {
		StructContext context = (StructContext) peekContext();
		if(context.fieldsStack.isEmpty()) {
      return EMPTY_LIST;
    }
    String fieldName = context.fieldsStack.peek();

    ListContext listContext = new ListContext();
    BasicDBList dbList = (BasicDBList) context.dbObject.get(fieldName);

    listContext.dbList = dbList;
    listContext.thriftObject = getThriftObject(context.thriftObject, fieldName);
    pushContext(listContext);
    return new TList(TType.LIST, dbList.size());
	}

	public void readListEnd() {
		popContext();
	}

	public TSet readSetBegin() throws TException {
		StructContext context = (StructContext) peekContext();
		if(context.fieldsStack.isEmpty()) {
      return EMPTY_SET;
    }
    String fieldName = context.fieldsStack.peek();

    ListContext listContext = new ListContext();
    BasicDBList dbList = (BasicDBList) context.dbObject.get(fieldName);

    listContext.dbList = dbList;
    listContext.thriftObject = getThriftObject(context.thriftObject, fieldName);
    pushContext(listContext);
    return new TSet(TType.SET, dbList.size());
	}

	public void readSetEnd() {
		popContext();
	}

	public boolean readBool() throws TException {
		return (readByte() == 1);
	}

	public byte readByte() throws TException {
		return ((Number) getCurrentFieldValue(TType.BYTE)).byteValue();
	}

	public short readI16() throws TException {
		return ((Number) getCurrentFieldValue(TType.I16)).shortValue();
	}

	public int readI32() throws TException {
		return ((Number) getCurrentFieldValue(TType.I32)).intValue();
	}

	public long readI64() throws TException {
		return ((Number) getCurrentFieldValue(TType.I64)).longValue();
	}

	public double readDouble() throws TException {
		return ((Number) getCurrentFieldValue(TType.DOUBLE)).doubleValue();
	}

	public String readString() throws TException {
		return (String) getCurrentFieldValue();
	}

	private Object getCurrentFieldValue() {
		Context context = peekContext();
		if( context instanceof StructContext && ((StructContext)context).fieldsStack.isEmpty() == false ) { 
			String fieldName = ((StructContext)context).fieldsStack.peek();
			// Extracts the dbobject
			return context.dbObject.get(fieldName);
		} else if(context instanceof  ListContext) {
			return ((ListContext)context).next();
		} else if(context instanceof  MapContext) {

            // IF YOU READ A KEY YOU MUST CONVERT THE STRING INTO NUMBER

			return ((MapContext)context).next();
		}
		return null;
	}


    private Object getCurrentFieldValue(byte ttype) {
        Context context = peekContext();
        if( context instanceof StructContext && ((StructContext)context).fieldsStack.isEmpty() == false ) {
            String fieldName = ((StructContext)context).fieldsStack.peek();
            // Extracts the dbobject
            Object fieldReaded = context.dbObject.get(fieldName);
            return fieldReaded;
        } else if(context instanceof  ListContext) {
            return ((ListContext)context).next();
        } else if(context instanceof  MapContext) {

            // IF YOU READ A KEY YOU MUST CONVERT THE STRING INTO NUMBER
            if( ((MapContext)context).isNextKey()) {
                switch( ttype ) {
                    case TType.BYTE:
                        return Byte.parseByte((String)((MapContext)context).next());
                    case TType.I32:
                    case TType.I16:
                        return Integer.parseInt((String)((MapContext)context).next());
                    case TType.I64:
                        return Long.parseLong((String)((MapContext)context).next());
                    case TType.DOUBLE:
                        return Double.parseDouble((String)((MapContext)context).next());
                }
            }

            return ((MapContext)context).next();
        }
        return null;
    }

	public String readStringBody(int size) throws TException {
		return "";
	}

	public ByteBuffer readBinary() throws TException {
		return ByteBuffer.wrap((byte[]) getCurrentFieldValue());
	}

	public void reset() {
		threadSafeContextStack.remove();
		threadSafeDBObject.remove();
    threadSafeFieldIds.remove();
	}
}
