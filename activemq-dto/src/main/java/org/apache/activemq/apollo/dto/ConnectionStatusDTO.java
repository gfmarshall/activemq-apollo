/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.apollo.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
@XmlRootElement(name="connection-status")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConnectionStatusDTO {

    /**
     * A unique id of the connection.
     */
	@XmlAttribute(name="id")
	public String id;

    /**
     * The state of the object.
     */
	@XmlAttribute(name="state")
	public String state;

    /**
     * The number of bytes that have been read from the connection.
     */
	@XmlAttribute(name="read-counter")
	public Long readCounter;


    /**
     * The number of bytes that have been written to the connection.
     */
	@XmlAttribute(name="write-counter")
	public Long writeCounter;

    /**
     * The protocol the connection is using.
     */
	@XmlAttribute(name="protocol")
	public String protocol;

    /**
     * The remote address of the connection
     */
	@XmlAttribute(name="remote-address")
	public String remoteAddress;

    /**
     * The connected user
     */
	@XmlAttribute(name="user")
	public String user;
}
