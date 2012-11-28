/*
 * Copyright 2012 Database Lab., Korea Univ.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tajo.storage.hcfile;

import tajo.catalog.proto.CatalogProtos.DataType;

public interface BlockMeta {

  /**
   * Return the type of this column
   *
   * @return the type of this column
   */
  public DataType getType();

  /**
   * Return the number of records in this file
   *
   * @return the number of records in this file
   */
  public int getRecordNum();

  public BlockMeta setRecordNum(int recordNum);

  public BlockMeta setStartRid(long rid);

  public long getStartRid();

  public boolean isSorted();

  public boolean isContiguous();

  public boolean isCompressed();
}
