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
package org.apache.activemq.apollo.openwire.command;

import org.apache.activemq.apollo.util.IntrospectionSupport;

import java.util.Map;


/**
 * 
 * @openwire:marshaller
 * @version $Revision: 1.11 $
 */
public abstract class BaseCommand implements Command {

    protected int commandId;
    protected boolean responseRequired;
    
    public void copy(BaseCommand copy) {
        copy.commandId = commandId;
        copy.responseRequired = responseRequired;
    }    

    /**
     * @openwire:property version=1
     */
    public int getCommandId() {
        return commandId;
    }

    public void setCommandId(int commandId) {
        this.commandId = commandId;
    }

    /**
     * @openwire:property version=1
     */
    public boolean isResponseRequired() {
        return responseRequired;
    }

    public void setResponseRequired(boolean responseRequired) {
        this.responseRequired = responseRequired;
    }

    public String toString() {
        return toString(null);
    }
    
    public String toString(Map<String, Object>overrideFields) {
    	return IntrospectionSupport.simpleName(this.getClass()) + " " +
               IntrospectionSupport.toString(this, BaseCommand.class, false, overrideFields);
    }
    
    public boolean isWireFormatInfo() {
        return false;
    }

    public boolean isBrokerInfo() {
        return false;
    }

    public boolean isResponse() {
        return false;
    }

    public boolean isMessageDispatch() {
        return false;
    }

    public boolean isMessage() {
        return false;
    }

    public boolean isMarshallAware() {
        return false;
    }

    public boolean isMessageAck() {
        return false;
    }

    public boolean isMessageDispatchNotification() {
        return false;
    }

    public boolean isShutdownInfo() {
        return false;
    }


    
}
