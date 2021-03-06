/**
 * Copyright (c) 2016 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.datatorrent.contrib.enrichment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectReader;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.esotericsoftware.kryo.NotNull;
import com.google.common.collect.Maps;
/**
 * @since 3.1.0
 */

public class FSLoader extends ReadOnlyBackup
{
  @NotNull
  private String fileName;

  private transient Path filePath;
  private transient FileSystem fs;
  private transient boolean connected;

  private static final transient ObjectMapper mapper = new ObjectMapper();
  private static final transient ObjectReader reader = mapper.reader(new TypeReference<Map<String, Object>>()
  {
  });
  private static final transient Logger logger = LoggerFactory.getLogger(FSLoader.class);

  public String getFileName()
  {
    return fileName;
  }

  public void setFileName(String fileName)
  {
    this.fileName = fileName;
  }

  @Override
  public Map<Object, Object> loadInitialData()
  {
    Map<Object, Object> result = null;
    FSDataInputStream in = null;
    BufferedReader bin = null;
    try {
      result = Maps.newHashMap();
      in = fs.open(filePath);
      bin = new BufferedReader(new InputStreamReader(in));
      String line;
      while ((line = bin.readLine()) != null) {
        try {
          Map<String, Object> tuple = reader.readValue(line);
          if (CollectionUtils.isEmpty(includeFields)) {
            if (includeFields == null) {
              includeFields = new ArrayList<String>();
            }
            for (Map.Entry<String, Object> e : tuple.entrySet()) {
              includeFields.add(e.getKey());
            }
          }
          ArrayList<Object> includeTuple = new ArrayList<Object>();
          for (String s: includeFields) {
            includeTuple.add(tuple.get(s));
          }
          result.put(getKey(tuple), includeTuple);
        } catch (JsonProcessingException parseExp) {
          logger.info("Unable to parse line {}", line);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (bin != null) {
        IOUtils.closeQuietly(bin);
      }
      if (in != null) {
        IOUtils.closeQuietly(in);
      }
      try {
        fs.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    logger.debug("loading initial data {}", result.size());
    return result;
  }

  private Object getKey(Map<String, Object> tuple)
  {
    ArrayList<Object> lst = new ArrayList<Object>();
    for (String key : lookupFields) {
      lst.add(tuple.get(key));
    }
    return lst;
  }

  @Override
  public Object get(Object key)
  {
    return null;
  }

  @Override
  public List<Object> getAll(List<Object> keys)
  {
    return null;
  }

  @Override
  public void connect() throws IOException
  {
    Configuration conf = new Configuration();
    this.filePath = new Path(fileName);
    this.fs = FileSystem.newInstance(filePath.toUri(), conf);
    if (!fs.isFile(filePath)) {
      throw new IOException("Provided path " + fileName + " is not a file");
    }
    connected = true;
  }

  @Override
  public void disconnect() throws IOException
  {
    if (fs != null) {
      fs.close();
    }
  }

  @Override
  public boolean isConnected()
  {
    return connected;
  }

  @Override
  public boolean needRefresh()
  {
    return false;
  }
}
