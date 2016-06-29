/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.physical.impl.filter;

import javax.inject.Named;

import org.apache.drill.exec.exception.SchemaChangeException;
import org.apache.drill.exec.exception.OutOfMemoryException;
import org.apache.drill.exec.ops.FragmentContext;
import org.apache.drill.exec.record.BatchSchema.SelectionVectorMode;
import org.apache.drill.exec.record.RecordBatch;
import org.apache.drill.exec.record.TransferPair;
import org.apache.drill.exec.record.selection.SelectionVector2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
//import org.apache.drill.exec.vector.NullableVarCharVector;
import org.apache.drill.exec.vector.NullableBigIntVector;

public abstract class FilterTemplate2 implements Filterer{
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FilterTemplate2.class);

  private SelectionVector2 outgoingSelectionVector;
  private SelectionVector2 incomingSelectionVector;
  private SelectionVectorMode svMode;
  private TransferPair[] transfers;
  private RecordBatch incoming;
  private ByteBuffer aBuf;
  private ByteBuffer bBuf;
  private ByteBuffer cBuf;
  private ByteBuffer dBuf;
  private ByteBuffer eBuf;
  private ByteBuffer fBuf;
  private ByteBuffer gBuf;
  private ByteBuffer resultBuf;
  private ByteBuffer recordCountBuf;
  private ByteBuffer dataSizeBuf;
  private ByteBuffer patternBuf;

  public native void init_device();
  public native void write_data(ByteBuffer aBuf, ByteBuffer bBuf, ByteBuffer cBuf, ByteBuffer dBuf, ByteBuffer eBuf, ByteBuffer fBuf, ByteBuffer gBuf, ByteBuffer dataSize, ByteBuffer recordCount);
  public native void execute_device();
  public native void read_result(ByteBuffer result, ByteBuffer recordCount);
  static { System.loadLibrary("drill_offload"); }

  @Override
  public void setup(FragmentContext context, RecordBatch incoming, RecordBatch outgoing, TransferPair[] transfers) throws SchemaChangeException{
    this.transfers = transfers;
    this.outgoingSelectionVector = outgoing.getSelectionVector2();
    this.svMode = incoming.getSchema().getSelectionVectorMode();
    this.incoming = incoming;

    dataSizeBuf = ByteBuffer.allocateDirect(4);
    dataSizeBuf.order(ByteOrder.LITTLE_ENDIAN);
    recordCountBuf = ByteBuffer.allocateDirect(4);
    recordCountBuf.order(ByteOrder.LITTLE_ENDIAN);
    resultBuf = ByteBuffer.allocateDirect(8192);

    switch(svMode){
    case NONE:
      break;
    case TWO_BYTE:
      this.incomingSelectionVector = incoming.getSelectionVector2();
      break;
    default:
      // SV4 is handled in FilterTemplate4
      throw new UnsupportedOperationException();
    }
    init_device();
    doSetup(context, incoming, outgoing);
  }

  private void doTransfers(){
    for(TransferPair t : transfers){
      t.transfer();
    }
  }

  public void filterBatch(int recordCount){
    if (recordCount == 0) {
      return;
    }
    if (! outgoingSelectionVector.allocateNewSafe(recordCount)) {
      throw new OutOfMemoryException("Unable to allocate filter batch");
    }
    switch(svMode){
    case NONE:
      filterBatchNoSV(recordCount);
      break;
    case TWO_BYTE:
      filterBatchSV2(recordCount);
      break;
    default:
      throw new UnsupportedOperationException();
    }
    doTransfers();
  }

  private void filterBatchSV2(int recordCount){
    int svIndex = 0;
    final int count = recordCount;
    for(int i = 0; i < count; i++){
      char index = incomingSelectionVector.getIndex(i);
      if(doEval(index, 0)){
        outgoingSelectionVector.setIndex(svIndex, index);
        svIndex++;
      }
    }
    outgoingSelectionVector.setRecordCount(svIndex);
  }

  private void filterBatchNoSV(int recordCount){
    int svIndex = 0;
    int[] fieldIds = new int[1];
    fieldIds[0] = 13;
    aBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    fieldIds[0] = 14;
    bBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    fieldIds[0] = 15;
    cBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    fieldIds[0] = 16;
    dBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    fieldIds[0] = 17;
    eBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    fieldIds[0] = 18;
    fBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    fieldIds[0] = 19;
    gBuf = ((NullableBigIntVector)incoming.getValueAccessorById(NullableBigIntVector.class, fieldIds).getValueVector()).getBuffer().nioBuffer();
    recordCountBuf.rewind();
    recordCountBuf.putInt(recordCount);
    dataSizeBuf.rewind();
    dataSizeBuf.putInt(recordCount * 8);
    write_data(aBuf, bBuf, cBuf, dBuf, eBuf, fBuf, gBuf, dataSizeBuf, recordCountBuf);
    execute_device();
    read_result(resultBuf, recordCountBuf);
    //for(int i = 0; i < recordCount; i++){
    //  if(doEval(i, 0)){
    //    outgoingSelectionVector.setIndex(svIndex, (char)i);
    //    svIndex++;
    //  }
    //}
    for (int i = 0; i < recordCount; i++) {
        if ((int)resultBuf.get(i) != 0) {
            outgoingSelectionVector.setIndex(svIndex, (char)i);
            svIndex++;
        }
    }
    outgoingSelectionVector.setRecordCount(svIndex);
  }

  public abstract void doSetup(@Named("context") FragmentContext context, @Named("incoming") RecordBatch incoming, @Named("outgoing") RecordBatch outgoing);
  public abstract boolean doEval(@Named("inIndex") int inIndex, @Named("outIndex") int outIndex);

}
