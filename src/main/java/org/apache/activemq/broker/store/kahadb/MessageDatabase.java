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
package org.apache.activemq.broker.store.kahadb;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.store.kahadb.data.KahaAddMessageCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaCommitCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaDestination;
import org.apache.activemq.broker.store.kahadb.data.KahaEntryType;
import org.apache.activemq.broker.store.kahadb.data.KahaLocalTransactionId;
import org.apache.activemq.broker.store.kahadb.data.KahaLocation;
import org.apache.activemq.broker.store.kahadb.data.KahaPrepareCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaRemoveDestinationCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaRemoveMessageCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaRollbackCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaSubscriptionCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaTraceCommand;
import org.apache.activemq.broker.store.kahadb.data.KahaTransactionInfo;
import org.apache.activemq.broker.store.kahadb.data.KahaXATransactionId;
import org.apache.activemq.broker.store.kahadb.data.KahaAddMessageCommand.KahaAddMessageCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaCommitCommand.KahaCommitCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaDestination.DestinationType;
import org.apache.activemq.broker.store.kahadb.data.KahaDestination.KahaDestinationBean;
import org.apache.activemq.broker.store.kahadb.data.KahaEntryType.KahaEntryTypeCreatable;
import org.apache.activemq.broker.store.kahadb.data.KahaLocalTransactionId.KahaLocalTransactionIdBean;
import org.apache.activemq.broker.store.kahadb.data.KahaLocation.KahaLocationBean;
import org.apache.activemq.broker.store.kahadb.data.KahaPrepareCommand.KahaPrepareCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaRemoveDestinationCommand.KahaRemoveDestinationCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaRemoveMessageCommand.KahaRemoveMessageCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaRollbackCommand.KahaRollbackCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaSubscriptionCommand.KahaSubscriptionCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaSubscriptionCommand.KahaSubscriptionCommandBuffer;
import org.apache.activemq.broker.store.kahadb.data.KahaTraceCommand.KahaTraceCommandBean;
import org.apache.activemq.broker.store.kahadb.data.KahaTransactionInfo.KahaTransactionInfoBean;
import org.apache.activemq.broker.store.kahadb.data.KahaXATransactionId.KahaXATransactionIdBean;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.apache.activemq.command.ActiveMQTempTopic;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.LocalTransactionId;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessageId;
import org.apache.activemq.command.SubscriptionInfo;
import org.apache.activemq.command.TransactionId;
import org.apache.activemq.command.XATransactionId;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.protobuf.Buffer;
import org.apache.activemq.protobuf.MessageBuffer;
import org.apache.activemq.protobuf.PBMessage;
import org.apache.activemq.store.AbstractMessageStore;
import org.apache.activemq.store.MessageRecoveryListener;
import org.apache.activemq.store.MessageStore;
import org.apache.activemq.store.TopicMessageStore;
import org.apache.activemq.store.TransactionRecoveryListener;
import org.apache.activemq.store.TransactionStore;
import org.apache.activemq.usage.MemoryUsage;
import org.apache.activemq.usage.SystemUsage;
import org.apache.activemq.util.Callback;
import org.apache.activemq.wireformat.WireFormat;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kahadb.index.BTreeIndex;
import org.apache.kahadb.index.BTreeVisitor;
import org.apache.kahadb.journal.Journal;
import org.apache.kahadb.journal.Location;
import org.apache.kahadb.page.Page;
import org.apache.kahadb.page.PageFile;
import org.apache.kahadb.page.Transaction;
import org.apache.kahadb.util.ByteSequence;
import org.apache.kahadb.util.DataByteArrayInputStream;
import org.apache.kahadb.util.DataByteArrayOutputStream;
import org.apache.kahadb.util.LockFile;
import org.apache.kahadb.util.LongMarshaller;
import org.apache.kahadb.util.Marshaller;
import org.apache.kahadb.util.StringMarshaller;

public class MessageDatabase {

    private static final Log LOG = LogFactory.getLog(MessageDatabase.class);
    private static final int DATABASE_LOCKED_WAIT_DELAY = 10 * 1000;

    public static final int CLOSED_STATE = 1;
    public static final int OPEN_STATE = 2;
    

    protected class Metadata {
        protected Page<Metadata> page;
        protected int state;
        protected BTreeIndex<String, StoredDestination> destinations;
        protected Location lastUpdate;
        protected Location firstInProgressTransactionLocation;

        public void read(DataInput is) throws IOException {
            state = is.readInt();
            destinations = new BTreeIndex<String, StoredDestination>(pageFile, is.readLong());
            if (is.readBoolean()) {
                lastUpdate = LocationMarshaller.INSTANCE.readPayload(is);
            } else {
                lastUpdate = null;
            }
            if (is.readBoolean()) {
                firstInProgressTransactionLocation = LocationMarshaller.INSTANCE.readPayload(is);
            } else {
                firstInProgressTransactionLocation = null;
            }
        }

        public void write(DataOutput os) throws IOException {
            os.writeInt(state);
            os.writeLong(destinations.getPageId());

            if (lastUpdate != null) {
                os.writeBoolean(true);
                LocationMarshaller.INSTANCE.writePayload(lastUpdate, os);
            } else {
                os.writeBoolean(false);
            }

            if (firstInProgressTransactionLocation != null) {
                os.writeBoolean(true);
                LocationMarshaller.INSTANCE.writePayload(firstInProgressTransactionLocation, os);
            } else {
                os.writeBoolean(false);
            }
        }
    }

    class MetadataMarshaller implements Marshaller<Metadata> {
        public Class<Metadata> getType() {
            return Metadata.class;
        }

        public Metadata readPayload(DataInput dataIn) throws IOException {
            Metadata rc = new Metadata();
            rc.read(dataIn);
            return rc;
        }

        public void writePayload(Metadata object, DataOutput dataOut) throws IOException {
            object.write(dataOut);
        }
    }

    protected PageFile pageFile;
    protected Journal journal;
    protected Metadata metadata = new Metadata();

    protected MetadataMarshaller metadataMarshaller = new MetadataMarshaller();

    protected boolean failIfDatabaseIsLocked;

    protected boolean deleteAllMessages;
    protected File directory;
    protected Thread checkpointThread;
    protected boolean enableJournalDiskSyncs=true;
    long checkpointInterval = 5*1000;
    long cleanupInterval = 30*1000;
    int journalMaxFileLength = Journal.DEFAULT_MAX_FILE_LENGTH;
    boolean enableIndexWriteAsync = false;
    int setIndexWriteBatchSize = PageFile.DEFAULT_WRITE_BATCH_SIZE; 
    
    protected AtomicBoolean started = new AtomicBoolean();
    protected AtomicBoolean opened = new AtomicBoolean();
    private LockFile lockFile;
    private WireFormat wireFormat = new OpenWireFormat();

    public MessageDatabase() {
    }

    public void setBrokerName(String brokerName) {
    }
    public void setUsageManager(SystemUsage usageManager) {
    }

    public TransactionStore createTransactionStore() throws IOException {
        return new TransactionStore(){
            
            public void commit(TransactionId txid, boolean wasPrepared) throws IOException {
                store(new KahaCommitCommandBean().setTransactionInfo(createTransactionInfo(txid)), true);
            }
            public void prepare(TransactionId txid) throws IOException {
                store(new KahaPrepareCommandBean().setTransactionInfo(createTransactionInfo(txid)), true);
            }
            public void rollback(TransactionId txid) throws IOException {
                store(new KahaRollbackCommandBean().setTransactionInfo(createTransactionInfo(txid)), false);
            }
            public void recover(TransactionRecoveryListener listener) throws IOException {
                for (Map.Entry<TransactionId, ArrayList<Operation>> entry : preparedTransactions.entrySet()) {
                    XATransactionId xid = (XATransactionId)entry.getKey();
                    ArrayList<Message> messageList = new ArrayList<Message>();
                    ArrayList<MessageAck> ackList = new ArrayList<MessageAck>();
                    
                    for (Operation op : entry.getValue()) {
                        if( op.getClass() == AddOpperation.class ) {
                            AddOpperation addOp = (AddOpperation)op;
                            Message msg = (Message)wireFormat.unmarshal( new DataInputStream(addOp.getCommand().getMessage().newInput()) );
                            messageList.add(msg);
                        } else {
                            RemoveOpperation rmOp = (RemoveOpperation)op;
                            MessageAck ack = (MessageAck)wireFormat.unmarshal( new DataInputStream(rmOp.getCommand().getAck().newInput()) );
                            ackList.add(ack);
                        }
                    }
                    
                    Message[] addedMessages = new Message[messageList.size()];
                    MessageAck[] acks = new MessageAck[ackList.size()];
                    messageList.toArray(addedMessages);
                    ackList.toArray(acks);
                    listener.recover(xid, addedMessages, acks);
                }
            }
            public void start() throws Exception {
            }
            public void stop() throws Exception {
            }
        };
    }

    public class KahaDBMessageStore extends AbstractMessageStore {
        protected KahaDestination dest;

        public KahaDBMessageStore(ActiveMQDestination destination) {
            super(destination);
            this.dest = convert( destination );
        }

        public ActiveMQDestination getDestination() {
            return destination;
        }

        public void addMessage(ConnectionContext context, Message message) throws IOException {
            KahaAddMessageCommandBean command = new KahaAddMessageCommandBean();
            command.setDestination(dest);
            command.setMessageId(message.getMessageId().toString());
            command.setTransactionInfo( createTransactionInfo(message.getTransactionId()) );

            org.apache.activemq.util.ByteSequence packet = wireFormat.marshal(message);
            command.setMessage(new Buffer(packet.getData(), packet.getOffset(), packet.getLength()));

            store(command, isEnableJournalDiskSyncs() && message.isResponseRequired());
            
        }
        
        public void removeMessage(ConnectionContext context, MessageAck ack) throws IOException {
            KahaRemoveMessageCommandBean command = new KahaRemoveMessageCommandBean();
            command.setDestination(dest);
            command.setMessageId(ack.getLastMessageId().toString());
            command.setTransactionInfo(createTransactionInfo(ack.getTransactionId()) );
            store(command, isEnableJournalDiskSyncs() && ack.isResponseRequired());
        }

        public void removeAllMessages(ConnectionContext context) throws IOException {
            KahaRemoveDestinationCommandBean command = new KahaRemoveDestinationCommandBean();
            command.setDestination(dest);
            store(command, true);
        }

        public Message getMessage(MessageId identity) throws IOException {
            final String key = identity.toString();
            
            // Hopefully one day the page file supports concurrent read operations... but for now we must
            // externally synchronize...
            Location location;
            synchronized(indexMutex) {
                location = pageFile.tx().execute(new Transaction.CallableClosure<Location, IOException>(){
                    public Location execute(Transaction tx) throws IOException {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        Long sequence = sd.messageIdIndex.get(tx, key);
                        if( sequence ==null ) {
                            return null;
                        }
                        return sd.orderIndex.get(tx, sequence).location;
                    }
                });
            }
            if( location == null ) {
                return null;
            }
            
            return loadMessage(location);
        }
        
        public int getMessageCount() throws IOException {
            synchronized(indexMutex) {
                return pageFile.tx().execute(new Transaction.CallableClosure<Integer, IOException>(){
                    public Integer execute(Transaction tx) throws IOException {
                        // Iterate through all index entries to get a count of messages in the destination.
                        StoredDestination sd = getStoredDestination(dest, tx);
                        int rc=0;
                        for (Iterator<Entry<Location, Long>> iterator = sd.locationIndex.iterator(tx); iterator.hasNext();) {
                            iterator.next();
                            rc++;
                        }
                        return rc;
                    }
                });
            }
        }

        public void recover(final MessageRecoveryListener listener) throws Exception {
            synchronized(indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<Exception>(){
                    public void execute(Transaction tx) throws Exception {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        for (Iterator<Entry<Long, MessageKeys>> iterator = sd.orderIndex.iterator(tx); iterator.hasNext();) {
                            Entry<Long, MessageKeys> entry = iterator.next();
                            listener.recoverMessage( loadMessage(entry.getValue().location) );
                        }
                    }
                });
            }
        }

        long cursorPos=0;
        
        public void recoverNextMessages(final int maxReturned, final MessageRecoveryListener listener) throws Exception {
            synchronized(indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<Exception>(){
                    public void execute(Transaction tx) throws Exception {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        Entry<Long, MessageKeys> entry=null;
                        int counter = 0;
                        for (Iterator<Entry<Long, MessageKeys>> iterator = sd.orderIndex.iterator(tx, cursorPos); iterator.hasNext();) {
                            entry = iterator.next();
                            listener.recoverMessage( loadMessage(entry.getValue().location ) );
                            counter++;
                            if( counter >= maxReturned ) {
                                break;
                            }
                        }
                        if( entry!=null ) {
                            cursorPos = entry.getKey()+1;
                        }
                    }
                });
            }
        }

        public void resetBatching() {
            cursorPos=0;
        }

        
        @Override
        public void setBatch(MessageId identity) throws IOException {
            final String key = identity.toString();
            
            // Hopefully one day the page file supports concurrent read operations... but for now we must
            // externally synchronize...
            Long location;
            synchronized(indexMutex) {
                location = pageFile.tx().execute(new Transaction.CallableClosure<Long, IOException>(){
                    public Long execute(Transaction tx) throws IOException {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        return sd.messageIdIndex.get(tx, key);
                    }
                });
            }
            if( location!=null ) {
                cursorPos=location+1;
            }
            
        }

        public void setMemoryUsage(MemoryUsage memoeyUSage) {
        }
        public void start() throws Exception {
        }
        public void stop() throws Exception {
        }
        
    }
        
    class KahaDBTopicMessageStore extends KahaDBMessageStore implements TopicMessageStore {
        public KahaDBTopicMessageStore(ActiveMQTopic destination) {
            super(destination);
        }
        
        public void acknowledge(ConnectionContext context, String clientId, String subscriptionName, MessageId messageId) throws IOException {
            KahaRemoveMessageCommandBean command = new KahaRemoveMessageCommandBean();
            command.setDestination(dest);
            command.setSubscriptionKey(subscriptionKey(clientId, subscriptionName));
            command.setMessageId(messageId.toString());
            // We are not passed a transaction info.. so we can't participate in a transaction.
            // Looks like a design issue with the TopicMessageStore interface.  Also we can't recover the original ack
            // to pass back to the XA recover method.
            // command.setTransactionInfo();
            store(command, false);
        }

        public void addSubsciption(SubscriptionInfo subscriptionInfo, boolean retroactive) throws IOException {
            String subscriptionKey = subscriptionKey(subscriptionInfo.getClientId(), subscriptionInfo.getSubscriptionName());
            KahaSubscriptionCommandBean command = new KahaSubscriptionCommandBean();
            command.setDestination(dest);
            command.setSubscriptionKey(subscriptionKey);
            command.setRetroactive(retroactive);
            org.apache.activemq.util.ByteSequence packet = wireFormat.marshal(subscriptionInfo);
            command.setSubscriptionInfo(new Buffer(packet.getData(), packet.getOffset(), packet.getLength()));
            store(command, isEnableJournalDiskSyncs() && true);
        }

        public void deleteSubscription(String clientId, String subscriptionName) throws IOException {
            KahaSubscriptionCommandBean command = new KahaSubscriptionCommandBean();
            command.setDestination(dest);
            command.setSubscriptionKey(subscriptionKey(clientId, subscriptionName));
            store(command, isEnableJournalDiskSyncs() && true);
        }

        public SubscriptionInfo[] getAllSubscriptions() throws IOException {
            
            final ArrayList<SubscriptionInfo> subscriptions = new ArrayList<SubscriptionInfo>();
            synchronized(indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<IOException>(){
                    public void execute(Transaction tx) throws IOException {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        for (Iterator<Entry<String, KahaSubscriptionCommand>> iterator = sd.subscriptions.iterator(tx); iterator.hasNext();) {
                            Entry<String, KahaSubscriptionCommand> entry = iterator.next();
                            SubscriptionInfo info = (SubscriptionInfo)wireFormat.unmarshal( new DataInputStream(entry.getValue().getSubscriptionInfo().newInput()) );
                            subscriptions.add(info);

                        }
                    }
                });
            }
            
            SubscriptionInfo[]rc=new SubscriptionInfo[subscriptions.size()];
            subscriptions.toArray(rc);
            return rc;
        }

        public SubscriptionInfo lookupSubscription(String clientId, String subscriptionName) throws IOException {
            final String subscriptionKey = subscriptionKey(clientId, subscriptionName);
            synchronized(indexMutex) {
                return pageFile.tx().execute(new Transaction.CallableClosure<SubscriptionInfo, IOException>(){
                    public SubscriptionInfo execute(Transaction tx) throws IOException {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        KahaSubscriptionCommand command = sd.subscriptions.get(tx, subscriptionKey);
                        if( command ==null ) {
                            return null;
                        }
                        return (SubscriptionInfo)wireFormat.unmarshal( new DataInputStream(command.getSubscriptionInfo().newInput()) );
                    }
                });
            }
        }
       
        public int getMessageCount(String clientId, String subscriptionName) throws IOException {
            final String subscriptionKey = subscriptionKey(clientId, subscriptionName);
            synchronized(indexMutex) {
                return pageFile.tx().execute(new Transaction.CallableClosure<Integer, IOException>(){
                    public Integer execute(Transaction tx) throws IOException {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        Long cursorPos = sd.subscriptionAcks.get(tx, subscriptionKey);
                        if ( cursorPos==null ) {
                            // The subscription might not exist.
                            return 0;
                        }
                        cursorPos += 1;
                        
                        int counter = 0;
                        for (Iterator<Entry<Long, MessageKeys>> iterator = sd.orderIndex.iterator(tx, cursorPos); iterator.hasNext();) {
                            iterator.next();
                            counter++;
                        }
                        return counter;
                    }
                });
            }        
        }

        public void recoverSubscription(String clientId, String subscriptionName, final MessageRecoveryListener listener) throws Exception {
            final String subscriptionKey = subscriptionKey(clientId, subscriptionName);
            synchronized(indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<Exception>(){
                    public void execute(Transaction tx) throws Exception {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        Long cursorPos = sd.subscriptionAcks.get(tx, subscriptionKey);
                        cursorPos += 1;
                        
                        for (Iterator<Entry<Long, MessageKeys>> iterator = sd.orderIndex.iterator(tx, cursorPos); iterator.hasNext();) {
                            Entry<Long, MessageKeys> entry = iterator.next();
                            listener.recoverMessage( loadMessage(entry.getValue().location ) );
                        }
                    }
                });
            }
        }

        public void recoverNextMessages(String clientId, String subscriptionName, final int maxReturned, final MessageRecoveryListener listener) throws Exception {
            final String subscriptionKey = subscriptionKey(clientId, subscriptionName);
            synchronized(indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<Exception>(){
                    public void execute(Transaction tx) throws Exception {
                        StoredDestination sd = getStoredDestination(dest, tx);
                        Long cursorPos = sd.subscriptionCursors.get(subscriptionKey);
                        if( cursorPos == null ) {
                            cursorPos = sd.subscriptionAcks.get(tx, subscriptionKey);
                            cursorPos += 1;
                        }
                        
                        Entry<Long, MessageKeys> entry=null;
                        int counter = 0;
                        for (Iterator<Entry<Long, MessageKeys>> iterator = sd.orderIndex.iterator(tx, cursorPos); iterator.hasNext();) {
                            entry = iterator.next();
                            listener.recoverMessage( loadMessage(entry.getValue().location ) );
                            counter++;
                            if( counter >= maxReturned ) {
                                break;
                            }
                        }
                        if( entry!=null ) {
                            sd.subscriptionCursors.put(subscriptionKey, cursorPos+1);
                        }
                    }
                });
            }
        }

        public void resetBatching(String clientId, String subscriptionName) {
            try {
                final String subscriptionKey = subscriptionKey(clientId, subscriptionName);
                synchronized(indexMutex) {
                    pageFile.tx().execute(new Transaction.Closure<IOException>(){
                        public void execute(Transaction tx) throws IOException {
                            StoredDestination sd = getStoredDestination(dest, tx);
                            sd.subscriptionCursors.remove(subscriptionKey);
                        }
                    });
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    String subscriptionKey(String clientId, String subscriptionName){
        return clientId+":"+subscriptionName;
    }
    
    public MessageStore createQueueMessageStore(ActiveMQQueue destination) throws IOException {
        return new KahaDBMessageStore(destination);
    }

    public TopicMessageStore createTopicMessageStore(ActiveMQTopic destination) throws IOException {
        return new KahaDBTopicMessageStore(destination);
    }

    /**
     * Cleanup method to remove any state associated with the given destination.
     * This method does not stop the message store (it might not be cached).
     *
     * @param destination Destination to forget
     */
    public void removeQueueMessageStore(ActiveMQQueue destination) {
    }

    /**
     * Cleanup method to remove any state associated with the given destination
     * This method does not stop the message store (it might not be cached).
     *
     * @param destination Destination to forget
     */
    public void removeTopicMessageStore(ActiveMQTopic destination) {
    }

    public void deleteAllMessages() throws IOException {
        deleteAllMessages=true;
    }
    
    
    public Set<ActiveMQDestination> getDestinations() {
        try {
            final HashSet<ActiveMQDestination> rc = new HashSet<ActiveMQDestination>();
            synchronized(indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<IOException>(){
                    public void execute(Transaction tx) throws IOException {
                        for (Iterator<Entry<String, StoredDestination>> iterator = metadata.destinations.iterator(tx); iterator.hasNext();) {
                            Entry<String, StoredDestination> entry = iterator.next();
                            rc.add(convert(entry.getKey()));
                        }
                    }
                });
            }
            return rc;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public long getLastMessageBrokerSequenceId() throws IOException {
        return 0;
    }
    
    public long size() {
        if ( !started.get() ) {
            return 0;
        }
        try {
            return journal.getDiskSize() + pageFile.getDiskSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void beginTransaction(ConnectionContext context) throws IOException {
        throw new IOException("Not yet implemented.");
    }
    public void commitTransaction(ConnectionContext context) throws IOException {
        throw new IOException("Not yet implemented.");
    }
    public void rollbackTransaction(ConnectionContext context) throws IOException {
        throw new IOException("Not yet implemented.");
    }
    
    public void checkpoint(boolean sync) throws IOException {
        checkpointCleanup(false);
    }
    
    ///////////////////////////////////////////////////////////////////
    // Internal helper methods.
    ///////////////////////////////////////////////////////////////////

    /**
     * @param location
     * @return
     * @throws IOException
     */
    Message loadMessage(Location location) throws IOException {
        KahaAddMessageCommand addMessage = (KahaAddMessageCommand)load(location);
        Message msg = (Message)wireFormat.unmarshal( new DataInputStream(addMessage.getMessage().newInput()) );
        return msg;
    }

    ///////////////////////////////////////////////////////////////////
    // Internal conversion methods.
    ///////////////////////////////////////////////////////////////////
    
    KahaTransactionInfo createTransactionInfo(TransactionId txid) {
        if( txid ==null ) {
            return null;
        }
        KahaTransactionInfoBean rc = new KahaTransactionInfoBean();
        
        // Link it up to the previous record that was part of the transaction.
        ArrayList<Operation> tx = inflightTransactions.get(txid);
        if( tx!=null ) {
            rc.setPreviousEntry(convert(tx.get(tx.size()-1).location));
        }
        
        if( txid.isLocalTransaction() ) {
            LocalTransactionId t = (LocalTransactionId)txid;
            KahaLocalTransactionIdBean kahaTxId = new KahaLocalTransactionIdBean();
            kahaTxId.setConnectionId(t.getConnectionId().getValue());
            kahaTxId.setTransacitonId(t.getValue());
            rc.setLocalTransacitonId(kahaTxId);
        } else {
            XATransactionId t = (XATransactionId)txid;
            KahaXATransactionIdBean kahaTxId = new KahaXATransactionIdBean();
            kahaTxId.setBranchQualifier(new Buffer(t.getBranchQualifier()));
            kahaTxId.setGlobalTransactionId(new Buffer(t.getGlobalTransactionId()));
            kahaTxId.setFormatId(t.getFormatId());
            rc.setXaTransacitonId(kahaTxId);
        }
        return rc;
    }
    
    KahaLocation convert(Location location) {
        KahaLocationBean rc = new KahaLocationBean();
        rc.setLogId(location.getDataFileId());
        rc.setOffset(location.getOffset());
        return rc;
    }
    
    KahaDestination convert(ActiveMQDestination dest) {
        KahaDestinationBean rc = new KahaDestinationBean();
        rc.setName(dest.getPhysicalName());
        switch( dest.getDestinationType() ) {
        case ActiveMQDestination.QUEUE_TYPE:
            rc.setType(DestinationType.QUEUE);
            return rc;
        case ActiveMQDestination.TOPIC_TYPE:
            rc.setType(DestinationType.TOPIC);
            return rc;
        case ActiveMQDestination.TEMP_QUEUE_TYPE:
            rc.setType(DestinationType.TEMP_QUEUE);
            return rc;
        case ActiveMQDestination.TEMP_TOPIC_TYPE:
            rc.setType(DestinationType.TEMP_TOPIC);
            return rc;
        default:
            return null;
        }
    }

    ActiveMQDestination convert(String dest) {
        int p = dest.indexOf(":");
        if( p<0 ) {
            throw new IllegalArgumentException("Not in the valid destination format");
        }
        int type = Integer.parseInt(dest.substring(0, p));
        String name = dest.substring(p+1);
        
        switch( KahaDestination.DestinationType.valueOf(type) ) {
        case QUEUE:
            return new ActiveMQQueue(name);
        case TOPIC:
            return new ActiveMQTopic(name);
        case TEMP_QUEUE:
            return new ActiveMQTempQueue(name);
        case TEMP_TOPIC:
            return new ActiveMQTempTopic(name);
        default:    
            throw new IllegalArgumentException("Not in the valid destination format");
        }
    }

    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
        	load();
        }
    }

    public void stop() throws Exception {
        if (started.compareAndSet(true, false)) {
            unload();
        }
    }

	private void loadPageFile() throws IOException {
		synchronized (indexMutex) {
		    final PageFile pageFile = getPageFile();
            pageFile.load();
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    if (pageFile.getPageCount() == 0) {
                        // First time this is created.. Initialize the metadata
                        Page<Metadata> page = tx.allocate();
                        assert page.getPageId() == 0;
                        page.set(metadata);
                        metadata.page = page;
                        metadata.state = CLOSED_STATE;
                        metadata.destinations = new BTreeIndex<String, StoredDestination>(pageFile, tx.allocate().getPageId());

                        tx.store(metadata.page, metadataMarshaller, true);
                    } else {
                        Page<Metadata> page = tx.load(0, metadataMarshaller);
                        metadata = page.get();
                        metadata.page = page;
                    }
                    metadata.destinations.setKeyMarshaller(StringMarshaller.INSTANCE);
                    metadata.destinations.setValueMarshaller(new StoredDestinationMarshaller());
                    metadata.destinations.load(tx);
                }
            });
            pageFile.flush();
            
            // Load up all the destinations since we need to scan all the indexes to figure out which journal files can be deleted.
            // Perhaps we should just keep an index of file
            storedDestinations.clear();
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    for (Iterator<Entry<String, StoredDestination>> iterator = metadata.destinations.iterator(tx); iterator.hasNext();) {
                        Entry<String, StoredDestination> entry = iterator.next();
                        StoredDestination sd = loadStoredDestination(tx, entry.getKey(), entry.getValue().subscriptions!=null);
                        storedDestinations.put(entry.getKey(), sd);
                    }
                }
            });
        }
	}
	
	/**
	 * @throws IOException
	 */
	public void open() throws IOException {
		if( opened.compareAndSet(false, true) ) {
            File lockFileName = new File(directory, "lock");
            lockFile = new LockFile(lockFileName, true);
	        if (failIfDatabaseIsLocked) {
	            lockFile.lock();
	        } else {
	            while (true) {
	                try {
	                    lockFile.lock();
	                    break;
	                } catch (IOException e) {
	                    LOG.info("Database "+lockFileName+" is locked... waiting " + (DATABASE_LOCKED_WAIT_DELAY / 1000) + " seconds for the database to be unlocked.");
	                    try {
	                        Thread.sleep(DATABASE_LOCKED_WAIT_DELAY);
	                    } catch (InterruptedException e1) {
	                    }
	                }
	            }
	        }
	        
            getJournal().start();
            
	        loadPageFile();
	        
	        checkpointThread = new Thread("ActiveMQ Journal Checkpoint Worker") {
	            public void run() {
	                try {
	                    long lastCleanup = System.currentTimeMillis();
	                    long lastCheckpoint = System.currentTimeMillis();
	                    
	                    // Sleep for a short time so we can periodically check 
	                    // to see if we need to exit this thread.
	                    long sleepTime = Math.min(checkpointInterval, 500);
	                    while (opened.get()) {
	                        Thread.sleep(sleepTime);
	                        long now = System.currentTimeMillis();
	                        if( now - lastCleanup >= cleanupInterval ) {
	                            checkpointCleanup(true);
	                            lastCleanup = now;
	                            lastCheckpoint = now;
	                        } else if( now - lastCheckpoint >= checkpointInterval ) {
	                            checkpointCleanup(false);
	                            lastCheckpoint = now;
	                        }
	                    }
	                } catch (InterruptedException e) {
	                    // Looks like someone really wants us to exit this thread...
	                }
	            }
	        };
	        checkpointThread.start();
            recover();
		}
	}
	
    public void load() throws IOException {
    	
        synchronized (indexMutex) {
	    	open();
	    	
	        if (deleteAllMessages) {
	            journal.delete();
	
	            pageFile.unload();
	            pageFile.delete();
	            metadata = new Metadata();
	            
	            LOG.info("Persistence store purged.");
	            deleteAllMessages = false;
	            
	            loadPageFile();
	        }
	        store(new KahaTraceCommandBean().setMessage("LOADED " + new Date()));

        }

    }

    
	public void close() throws IOException, InterruptedException {
		if( opened.compareAndSet(true, false)) {
	        synchronized (indexMutex) {
	            pageFile.unload();
	            metadata = new Metadata();
	        }
	        journal.close();
	        checkpointThread.join();
	        lockFile.unlock();
	        lockFile=null;
		}
	}
	
    public void unload() throws IOException, InterruptedException {
        synchronized (indexMutex) {
            if( pageFile.isLoaded() ) {
                metadata.state = CLOSED_STATE;
                metadata.firstInProgressTransactionLocation = getFirstInProgressTxLocation();
    
                pageFile.tx().execute(new Transaction.Closure<IOException>() {
                    public void execute(Transaction tx) throws IOException {
                        tx.store(metadata.page, metadataMarshaller, true);
                    }
                });
                close();
            }
        }
    }

    /**
     * @return
     */
    private Location getFirstInProgressTxLocation() {
        Location l = null;
        if (!inflightTransactions.isEmpty()) {
            l = inflightTransactions.values().iterator().next().get(0).getLocation();
        }
        if (!preparedTransactions.isEmpty()) {
            Location t = preparedTransactions.values().iterator().next().get(0).getLocation();
            if (l==null || t.compareTo(l) <= 0) {
                l = t;
            }
        }
        return l;
    }

    /**
     * Move all the messages that were in the journal into long term storage. We
     * just replay and do a checkpoint.
     * 
     * @throws IOException
     * @throws IOException
     * @throws InvalidLocationException
     * @throws IllegalStateException
     */
    private void recover() throws IllegalStateException, IOException {
        synchronized (indexMutex) {
	        long start = System.currentTimeMillis();
	        
	        Location recoveryPosition = getRecoveryPosition();
	        if( recoveryPosition!=null ) {
		        int redoCounter = 0;
		        while (recoveryPosition != null) {
		            KahaEntryTypeCreatable message = load(recoveryPosition);
		            metadata.lastUpdate = recoveryPosition;
		            process(message, recoveryPosition);
		            redoCounter++;
		            recoveryPosition = journal.getNextLocation(recoveryPosition);
		        }
		        long end = System.currentTimeMillis();
	        	LOG.info("Replayed " + redoCounter + " operations from the journal in " + ((end - start) / 1000.0f) + " seconds.");
	        }
	     
	        // We may have to undo some index updates.
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    recoverIndex(tx);
                }
            });
        }
    }
    
	protected void recoverIndex(Transaction tx) throws IOException {
        long start = System.currentTimeMillis();
        // It is possible index updates got applied before the journal updates.. 
        // in that case we need to removed references to messages that are not in the journal
        final Location lastAppendLocation = journal.getLastAppendLocation();
        long undoCounter=0;
        
        // Go through all the destinations to see if they have messages past the lastAppendLocation
        for (StoredDestination sd : storedDestinations.values()) {
        	
            final ArrayList<Long> matches = new ArrayList<Long>();
            // Find all the Locations that are >= than the last Append Location.
            sd.locationIndex.visit(tx, new BTreeVisitor.GTEVisitor<Location, Long>(lastAppendLocation) {
				@Override
				protected void matched(Location key, Long value) {
					matches.add(value);
				}
            });
            
            
            for (Long sequenceId : matches) {
                MessageKeys keys = sd.orderIndex.remove(tx, sequenceId);
                sd.locationIndex.remove(tx, keys.location);
                sd.messageIdIndex.remove(tx, keys.messageId);
                undoCounter++;
                // TODO: do we need to modify the ack positions for the pub sub case?
			}
        }
        long end = System.currentTimeMillis();
        if( undoCounter > 0 ) {
        	// The rolledback operations are basically in flight journal writes.  To avoid getting these the end user
        	// should do sync writes to the journal.
	        LOG.info("Rolled back " + undoCounter + " operations from the index in " + ((end - start) / 1000.0f) + " seconds.");
        }
	}

	private Location nextRecoveryPosition;
	private Location lastRecoveryPosition;

	public void incrementalRecover() throws IOException {
        synchronized (indexMutex) {
	        if( nextRecoveryPosition == null ) {
	        	if( lastRecoveryPosition==null ) {
	        		nextRecoveryPosition = getRecoveryPosition();
	        	} else {
	                nextRecoveryPosition = journal.getNextLocation(lastRecoveryPosition);
	        	}        	
	        }
	        while (nextRecoveryPosition != null) {
	        	lastRecoveryPosition = nextRecoveryPosition;
	            metadata.lastUpdate = lastRecoveryPosition;
	            KahaEntryTypeCreatable message = load(lastRecoveryPosition);
	            process(message, lastRecoveryPosition);            
	            nextRecoveryPosition = journal.getNextLocation(lastRecoveryPosition);
	        }
        }
	}
	
    public Location getLastUpdatePosition() throws IOException {
        return metadata.lastUpdate;
    }
    
	private Location getRecoveryPosition() throws IOException {
		
        // If we need to recover the transactions..
        if (metadata.firstInProgressTransactionLocation != null) {
            return metadata.firstInProgressTransactionLocation;
        }
        
        // Perhaps there were no transactions...
        if( metadata.lastUpdate!=null) {
            // Start replay at the record after the last one recorded in the index file.
            return journal.getNextLocation(metadata.lastUpdate);
        }
        
        // This loads the first position.
        return journal.getNextLocation(null);
	}

    protected void checkpointCleanup(final boolean cleanup) {
        try {
        	long start = System.currentTimeMillis();
            synchronized (indexMutex) {
            	if( !opened.get() ) {
            		return;
            	}
                pageFile.tx().execute(new Transaction.Closure<IOException>() {
                    public void execute(Transaction tx) throws IOException {
                        checkpointUpdate(tx, cleanup);
                    }
                });
            }
        	long end = System.currentTimeMillis();
        	if( end-start > 100 ) { 
        		LOG.warn("KahaDB Cleanup took "+(end-start));
        	}
        } catch (IOException e) {
        	e.printStackTrace();
        }
    }

    
	public void checkpoint(Callback closure) throws Exception {
        synchronized (indexMutex) {
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    checkpointUpdate(tx, false);
                }
            });
            closure.execute();
        }
	}

    // /////////////////////////////////////////////////////////////////
    // Methods call by the broker to update and query the store.
    // /////////////////////////////////////////////////////////////////
    public Location store(KahaEntryTypeCreatable data) throws IOException {
        return store(data, false);
    }

    /**
     * All updated are are funneled through this method. The updates a converted
     * to a JournalMessage which is logged to the journal and then the data from
     * the JournalMessage is used to update the index just like it would be done
     * durring a recovery process.
     */
    @SuppressWarnings("unchecked")
    public Location store(KahaEntryTypeCreatable data, boolean sync) throws IOException {
    	MessageBuffer message = ((PBMessage) data).freeze();
        int size = message.serializedSizeUnframed();
        DataByteArrayOutputStream os = new DataByteArrayOutputStream(size + 1);
        os.writeByte(data.toKahaEntryType().getNumber());
        message.writeUnframed(os);

        long start = System.currentTimeMillis();
        Location location = journal.write(os.toByteSequence(), sync);
        long start2 = System.currentTimeMillis();
        process(data, location);
    	long end = System.currentTimeMillis();
    	if( end-start > 100 ) { 
    		LOG.warn("KahaDB long enqueue time: Journal Add Took: "+(start2-start)+" ms, Index Update took "+(end-start2)+" ms");
    	}

        synchronized (indexMutex) {
        	metadata.lastUpdate = location;
        }
        return location;
    }

    /**
     * Loads a previously stored JournalMessage
     * 
     * @param location
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public KahaEntryTypeCreatable load(Location location) throws IOException {
        ByteSequence data = journal.read(location);
        DataByteArrayInputStream is = new DataByteArrayInputStream(data);
        byte readByte = is.readByte();
        KahaEntryType type = KahaEntryType.valueOf(readByte);
        if( type == null ) {
            throw new IOException("Could not load journal record. Invalid location: "+location);
        }
        MessageBuffer message = type.parseUnframed(new Buffer(data.data, data.offset+1, data.length-1));
        return (KahaEntryTypeCreatable)message;
    }

    // /////////////////////////////////////////////////////////////////
    // Journaled record processing methods. Once the record is journaled,
    // these methods handle applying the index updates. These may be called
    // from the recovery method too so they need to be idempotent
    // /////////////////////////////////////////////////////////////////

    private void process(KahaEntryTypeCreatable data, final Location location) throws IOException {
        switch(data.toKahaEntryType()) {
        case KAHA_ADD_MESSAGE_COMMAND:
            process((KahaAddMessageCommand)data, location);
            return;
        case KAHA_COMMIT_COMMAND:
            process((KahaCommitCommand)data, location);
            return;
        case KAHA_PREPARE_COMMAND:
            process((KahaPrepareCommand)data, location);
            return;
        case KAHA_REMOVE_DESTINATION_COMMAND:
            process((KahaRemoveDestinationCommand)data, location);
            return;
        case KAHA_REMOVE_MESSAGE_COMMAND:
            process((KahaRemoveMessageCommand)data, location);
            return;
        case KAHA_ROLLBACK_COMMAND:
            process((KahaRollbackCommand)data, location);
            return;
        case KAHA_SUBSCRIPTION_COMMAND:
            process((KahaSubscriptionCommand)data, location);
            return;
        case KAHA_TRACE_COMMAND:
            process((KahaTraceCommand)data, location);
            return;
        }
    }

    private void process(final KahaAddMessageCommand command, final Location location) throws IOException {
        if (command.hasTransactionInfo()) {
            synchronized (indexMutex) {
                ArrayList<Operation> inflightTx = getInflightTx(command.getTransactionInfo(), location);
                inflightTx.add(new AddOpperation(command, location));
            }
        } else {
            synchronized (indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<IOException>() {
                    public void execute(Transaction tx) throws IOException {
                        upadateIndex(tx, command, location);
                    }
                });
            }
        }
    }

    protected void process(final KahaRemoveMessageCommand command, final Location location) throws IOException {
        if (command.hasTransactionInfo()) {
            synchronized (indexMutex) {
                ArrayList<Operation> inflightTx = getInflightTx(command.getTransactionInfo(), location);
                inflightTx.add(new RemoveOpperation(command, location));
            }
        } else {
            synchronized (indexMutex) {
                pageFile.tx().execute(new Transaction.Closure<IOException>() {
                    public void execute(Transaction tx) throws IOException {
                        updateIndex(tx, command, location);
                    }
                });
            }
        }

    }

    protected void process(final KahaRemoveDestinationCommand command, final Location location) throws IOException {
        synchronized (indexMutex) {
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    updateIndex(tx, command, location);
                }
            });
        }
    }

    protected void process(final KahaSubscriptionCommand command, final Location location) throws IOException {
        synchronized (indexMutex) {
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    updateIndex(tx, command, location);
                }
            });
        }
    }

    protected void process(KahaCommitCommand command, Location location) throws IOException {
        TransactionId key = key(command.getTransactionInfo());
        synchronized (indexMutex) {
            ArrayList<Operation> inflightTx = inflightTransactions.remove(key);
            if (inflightTx == null) {
                inflightTx = preparedTransactions.remove(key);
            }
            if (inflightTx == null) {
                return;
            }

            final ArrayList<Operation> messagingTx = inflightTx;
            pageFile.tx().execute(new Transaction.Closure<IOException>() {
                public void execute(Transaction tx) throws IOException {
                    for (Operation op : messagingTx) {
                        op.execute(tx);
                    }
                }
            });
        }
    }

    protected void process(KahaPrepareCommand command, Location location) {
        synchronized (indexMutex) {
            TransactionId key = key(command.getTransactionInfo());
            ArrayList<Operation> tx = inflightTransactions.remove(key);
            if (tx != null) {
                preparedTransactions.put(key, tx);
            }
        }
    }

    protected void process(KahaRollbackCommand command, Location location) {
        synchronized (indexMutex) {
            TransactionId key = key(command.getTransactionInfo());
            ArrayList<Operation> tx = inflightTransactions.remove(key);
            if (tx == null) {
                preparedTransactions.remove(key);
            }
        }
    }

    // /////////////////////////////////////////////////////////////////
    // These methods do the actual index updates.
    // /////////////////////////////////////////////////////////////////

    protected final Object indexMutex = new Object();
	private final HashSet<Integer> journalFilesBeingReplicated = new HashSet<Integer>();

    private void upadateIndex(Transaction tx, KahaAddMessageCommand command, Location location) throws IOException {
        StoredDestination sd = getStoredDestination(command.getDestination(), tx);

        // Skip adding the message to the index if this is a topic and there are
        // no subscriptions.
        if (sd.subscriptions != null && sd.ackPositions.isEmpty()) {
            return;
        }

        // Add the message.
        long id = sd.nextMessageId++;
        Long previous = sd.locationIndex.put(tx, location, id);
        if( previous == null ) {
            sd.messageIdIndex.put(tx, command.getMessageId(), id);
            sd.orderIndex.put(tx, id, new MessageKeys(command.getMessageId(), location));
        } else {
            // restore the previous value.. Looks like this was a redo of a previously
            // added message.  We don't want to assing it a new id as the other indexes would 
            // be wrong..
            sd.locationIndex.put(tx, location, previous);
        }
        
    }

    private void updateIndex(Transaction tx, KahaRemoveMessageCommand command, Location ackLocation) throws IOException {
        StoredDestination sd = getStoredDestination(command.getDestination(), tx);
        if (!command.hasSubscriptionKey()) {
            
            // In the queue case we just remove the message from the index..
            Long sequenceId = sd.messageIdIndex.remove(tx, command.getMessageId());
            if (sequenceId != null) {
                MessageKeys keys = sd.orderIndex.remove(tx, sequenceId);
                sd.locationIndex.remove(tx, keys.location);
            }
        } else {
            // In the topic case we need remove the message once it's been acked
            // by all the subs
            Long sequence = sd.messageIdIndex.get(tx, command.getMessageId());

            // Make sure it's a valid message id...
            if (sequence != null) {
                String subscriptionKey = command.getSubscriptionKey();
                Long prev = sd.subscriptionAcks.put(tx, subscriptionKey, sequence);

                // The following method handles deleting un-referenced messages.
                removeAckLocation(tx, sd, subscriptionKey, prev);

                // Add it to the new location set.
                addAckLocation(sd, sequence, subscriptionKey);
            }

        }
    }

    private void updateIndex(Transaction tx, KahaRemoveDestinationCommand command, Location location) throws IOException {
        StoredDestination sd = getStoredDestination(command.getDestination(), tx);
        sd.orderIndex.clear(tx);
        sd.orderIndex.unload(tx);
        tx.free(sd.orderIndex.getPageId());
        
        sd.locationIndex.clear(tx);
        sd.locationIndex.unload(tx);
        tx.free(sd.locationIndex.getPageId());

        sd.messageIdIndex.clear(tx);
        sd.messageIdIndex.unload(tx);
        tx.free(sd.messageIdIndex.getPageId());

        if (sd.subscriptions != null) {
            sd.subscriptions.clear(tx);
            sd.subscriptions.unload(tx);
            tx.free(sd.subscriptions.getPageId());

            sd.subscriptionAcks.clear(tx);
            sd.subscriptionAcks.unload(tx);
            tx.free(sd.subscriptionAcks.getPageId());
        }

        String key = key(command.getDestination());
        storedDestinations.remove(key);
        metadata.destinations.remove(tx, key);
    }

    private void updateIndex(Transaction tx, KahaSubscriptionCommand command, Location location) throws IOException {
        StoredDestination sd = getStoredDestination(command.getDestination(), tx);

        // If set then we are creating it.. otherwise we are destroying the sub
        if (command.hasSubscriptionInfo()) {
            String subscriptionKey = command.getSubscriptionKey();
            sd.subscriptions.put(tx, subscriptionKey, command);
            long ackLocation=-1;
            if (!command.getRetroactive()) {
                ackLocation = sd.nextMessageId-1;
            }

            sd.subscriptionAcks.put(tx, subscriptionKey, ackLocation);
            addAckLocation(sd, ackLocation, subscriptionKey);
        } else {
            // delete the sub...
            String subscriptionKey = command.getSubscriptionKey();
            sd.subscriptions.remove(tx, subscriptionKey);
            Long prev = sd.subscriptionAcks.remove(tx, subscriptionKey);
            if( prev!=null ) {
                removeAckLocation(tx, sd, subscriptionKey, prev);
            }
        }

    }
    
    /**
     * @param tx
     * @throws IOException
     */
    private void checkpointUpdate(Transaction tx, boolean cleanup) throws IOException {

        LOG.debug("Checkpoint started.");
        
        metadata.state = OPEN_STATE;
        metadata.firstInProgressTransactionLocation = getFirstInProgressTxLocation();
        tx.store(metadata.page, metadataMarshaller, true);
        pageFile.flush();

        if( cleanup ) {
        	
        	final TreeSet<Integer> gcCandidateSet = new TreeSet<Integer>(journal.getFileMap().keySet());
        	
        	// Don't GC files under replication
        	if( journalFilesBeingReplicated!=null ) {
        		gcCandidateSet.removeAll(journalFilesBeingReplicated);
        	}
        	
        	// Don't GC files after the first in progress tx
        	Location firstTxLocation = metadata.lastUpdate;
            if( metadata.firstInProgressTransactionLocation!=null ) {
                firstTxLocation = metadata.firstInProgressTransactionLocation;
            }
            
            if( firstTxLocation!=null ) {
            	while( !gcCandidateSet.isEmpty() ) {
            		Integer last = gcCandidateSet.last();
            		if( last >= firstTxLocation.getDataFileId() ) {
            			gcCandidateSet.remove(last);
            		} else {
            			break;
            		}
            	}
            }

            // Go through all the destinations to see if any of them can remove GC candidates.
            for (StoredDestination sd : storedDestinations.values()) {
            	if( gcCandidateSet.isEmpty() ) {
                	break;
                }
                
                // Use a visitor to cut down the number of pages that we load
                sd.locationIndex.visit(tx, new BTreeVisitor<Location, Long>() {
                    int last=-1;
                    public boolean isInterestedInKeysBetween(Location first, Location second) {
                    	if( first==null ) {
                    		SortedSet<Integer> subset = gcCandidateSet.headSet(second.getDataFileId()+1);
                    		if( !subset.isEmpty() && subset.last() == second.getDataFileId() ) {
                    			subset.remove(second.getDataFileId());
                    		}
							return !subset.isEmpty();
                    	} else if( second==null ) {
                    		SortedSet<Integer> subset = gcCandidateSet.tailSet(first.getDataFileId());
                    		if( !subset.isEmpty() && subset.first() == first.getDataFileId() ) {
                    			subset.remove(first.getDataFileId());
                    		}
							return !subset.isEmpty();
                    	} else {
                    		SortedSet<Integer> subset = gcCandidateSet.subSet(first.getDataFileId(), second.getDataFileId()+1);
                    		if( !subset.isEmpty() && subset.first() == first.getDataFileId() ) {
                    			subset.remove(first.getDataFileId());
                    		}
                    		if( !subset.isEmpty() && subset.last() == second.getDataFileId() ) {
                    			subset.remove(second.getDataFileId());
                    		}
							return !subset.isEmpty();
                    	}
                    }
    
                    public void visit(List<Location> keys, List<Long> values) {
                    	for (Location l : keys) {
                            int fileId = l.getDataFileId();
							if( last != fileId ) {
                        		gcCandidateSet.remove(fileId);
                                last = fileId;
                            }
						}                        
                    }
    
                });
            }

            if( !gcCandidateSet.isEmpty() ) {
	            LOG.debug("Cleanup removing the data files: "+gcCandidateSet);
	            journal.removeDataFiles(gcCandidateSet);
            }
        }
        
        LOG.debug("Checkpoint done.");
    }
    
    public HashSet<Integer> getJournalFilesBeingReplicated() {
		return journalFilesBeingReplicated;
	}

    // /////////////////////////////////////////////////////////////////
    // StoredDestination related implementation methods.
    // /////////////////////////////////////////////////////////////////


	private final HashMap<String, StoredDestination> storedDestinations = new HashMap<String, StoredDestination>();

    class StoredSubscription {
        SubscriptionInfo subscriptionInfo;
        String lastAckId;
        Location lastAckLocation;
        Location cursor;
    }
    
    static class MessageKeys {
        final String messageId;
        final Location location;
        
        public MessageKeys(String messageId, Location location) {
            this.messageId=messageId;
            this.location=location;
        }
        
        @Override
        public String toString() {
            return "["+messageId+","+location+"]";
        }
    }
    
    static protected class MessageKeysMarshaller implements Marshaller<MessageKeys> {
        static final MessageKeysMarshaller INSTANCE = new MessageKeysMarshaller();
        
        public Class<MessageKeys> getType() {
            return MessageKeys.class;
        }

        public MessageKeys readPayload(DataInput dataIn) throws IOException {
            return new MessageKeys(dataIn.readUTF(), LocationMarshaller.INSTANCE.readPayload(dataIn));
        }

        public void writePayload(MessageKeys object, DataOutput dataOut) throws IOException {
            dataOut.writeUTF(object.messageId);
            LocationMarshaller.INSTANCE.writePayload(object.location, dataOut);
        }
    }
    
    static class StoredDestination {
        long nextMessageId;
        BTreeIndex<Long, MessageKeys> orderIndex;
        BTreeIndex<Location, Long> locationIndex;
        BTreeIndex<String, Long> messageIdIndex;

        // These bits are only set for Topics
        BTreeIndex<String, KahaSubscriptionCommand> subscriptions;
        BTreeIndex<String, Long> subscriptionAcks;
        HashMap<String, Long> subscriptionCursors;
        TreeMap<Long, HashSet<String>> ackPositions;
    }

    protected class StoredDestinationMarshaller implements Marshaller<StoredDestination> {
        public Class<StoredDestination> getType() {
            return StoredDestination.class;
        }

        public StoredDestination readPayload(DataInput dataIn) throws IOException {
            StoredDestination value = new StoredDestination();
            value.orderIndex = new BTreeIndex<Long, MessageKeys>(pageFile, dataIn.readLong());
            value.locationIndex = new BTreeIndex<Location, Long>(pageFile, dataIn.readLong());
            value.messageIdIndex = new BTreeIndex<String, Long>(pageFile, dataIn.readLong());

            if (dataIn.readBoolean()) {
                value.subscriptions = new BTreeIndex<String, KahaSubscriptionCommand>(pageFile, dataIn.readLong());
                value.subscriptionAcks = new BTreeIndex<String, Long>(pageFile, dataIn.readLong());
            }
            return value;
        }

        public void writePayload(StoredDestination value, DataOutput dataOut) throws IOException {
            dataOut.writeLong(value.orderIndex.getPageId());
            dataOut.writeLong(value.locationIndex.getPageId());
            dataOut.writeLong(value.messageIdIndex.getPageId());
            if (value.subscriptions != null) {
                dataOut.writeBoolean(true);
                dataOut.writeLong(value.subscriptions.getPageId());
                dataOut.writeLong(value.subscriptionAcks.getPageId());
            } else {
                dataOut.writeBoolean(false);
            }
        }
    }

    static class LocationMarshaller implements Marshaller<Location> {
        final static LocationMarshaller INSTANCE = new LocationMarshaller();

        public Class<Location> getType() {
            return Location.class;
        }

        public Location readPayload(DataInput dataIn) throws IOException {
            Location rc = new Location();
            rc.setDataFileId(dataIn.readInt());
            rc.setOffset(dataIn.readInt());
            return rc;
        }

        public void writePayload(Location object, DataOutput dataOut) throws IOException {
            dataOut.writeInt(object.getDataFileId());
            dataOut.writeInt(object.getOffset());
        }
    }

    static class KahaSubscriptionCommandMarshaller implements Marshaller<KahaSubscriptionCommand> {
        final static KahaSubscriptionCommandMarshaller INSTANCE = new KahaSubscriptionCommandMarshaller();

        public Class<KahaSubscriptionCommand> getType() {
            return KahaSubscriptionCommand.class;
        }

        public KahaSubscriptionCommand readPayload(DataInput dataIn) throws IOException {
            return KahaSubscriptionCommandBuffer.parseFramed((InputStream)dataIn);
        }

        public void writePayload(KahaSubscriptionCommand object, DataOutput dataOut) throws IOException {
            object.freeze().writeFramed((OutputStream)dataOut);
        }
    }

    protected StoredDestination getStoredDestination(KahaDestination destination, Transaction tx) throws IOException {
        String key = key(destination);
        StoredDestination rc = storedDestinations.get(key);
        if (rc == null) {
            boolean topic = destination.getType() == KahaDestination.DestinationType.TOPIC || destination.getType() == KahaDestination.DestinationType.TEMP_TOPIC;
            rc = loadStoredDestination(tx, key, topic);
            // Cache it. We may want to remove/unload destinations from the
            // cache that are not used for a while
            // to reduce memory usage.
            storedDestinations.put(key, rc);
        }
        return rc;
    }

    /**
     * @param tx
     * @param key
     * @param topic
     * @return
     * @throws IOException
     */
    private StoredDestination loadStoredDestination(Transaction tx, String key, boolean topic) throws IOException {
        // Try to load the existing indexes..
        StoredDestination rc = metadata.destinations.get(tx, key);
        if (rc == null) {
            // Brand new destination.. allocate indexes for it.
            rc = new StoredDestination();
            rc.orderIndex = new BTreeIndex<Long, MessageKeys>(pageFile, tx.allocate());
            rc.locationIndex = new BTreeIndex<Location, Long>(pageFile, tx.allocate());
            rc.messageIdIndex = new BTreeIndex<String, Long>(pageFile, tx.allocate());

            if (topic) {
                rc.subscriptions = new BTreeIndex<String, KahaSubscriptionCommand>(pageFile, tx.allocate());
                rc.subscriptionAcks = new BTreeIndex<String, Long>(pageFile, tx.allocate());
            }
            metadata.destinations.put(tx, key, rc);
        }

        // Configure the marshalers and load.
        rc.orderIndex.setKeyMarshaller(LongMarshaller.INSTANCE);
        rc.orderIndex.setValueMarshaller(MessageKeysMarshaller.INSTANCE);
        rc.orderIndex.load(tx);

        // Figure out the next key using the last entry in the destination.
        Entry<Long, MessageKeys> lastEntry = rc.orderIndex.getLast(tx);
        if( lastEntry!=null ) {
            rc.nextMessageId = lastEntry.getKey()+1;
        }

        rc.locationIndex.setKeyMarshaller(LocationMarshaller.INSTANCE);
        rc.locationIndex.setValueMarshaller(LongMarshaller.INSTANCE);
        rc.locationIndex.load(tx);

        rc.messageIdIndex.setKeyMarshaller(StringMarshaller.INSTANCE);
        rc.messageIdIndex.setValueMarshaller(LongMarshaller.INSTANCE);
        rc.messageIdIndex.load(tx);
        
        // If it was a topic...
        if (topic) {

            rc.subscriptions.setKeyMarshaller(StringMarshaller.INSTANCE);
            rc.subscriptions.setValueMarshaller(KahaSubscriptionCommandMarshaller.INSTANCE);
            rc.subscriptions.load(tx);

            rc.subscriptionAcks.setKeyMarshaller(StringMarshaller.INSTANCE);
            rc.subscriptionAcks.setValueMarshaller(LongMarshaller.INSTANCE);
            rc.subscriptionAcks.load(tx);

            rc.ackPositions = new TreeMap<Long, HashSet<String>>();
            rc.subscriptionCursors = new HashMap<String, Long>();

            for (Iterator<Entry<String, Long>> iterator = rc.subscriptionAcks.iterator(tx); iterator.hasNext();) {
                Entry<String, Long> entry = iterator.next();
                addAckLocation(rc, entry.getValue(), entry.getKey());
            }

        }
        return rc;
    }

    /**
     * @param sd
     * @param messageSequence
     * @param subscriptionKey
     */
    private void addAckLocation(StoredDestination sd, Long messageSequence, String subscriptionKey) {
        HashSet<String> hs = sd.ackPositions.get(messageSequence);
        if (hs == null) {
            hs = new HashSet<String>();
            sd.ackPositions.put(messageSequence, hs);
        }
        hs.add(subscriptionKey);
    }

    /**
     * @param tx
     * @param sd
     * @param subscriptionKey
     * @param sequenceId
     * @throws IOException
     */
    private void removeAckLocation(Transaction tx, StoredDestination sd, String subscriptionKey, Long sequenceId) throws IOException {
        // Remove the sub from the previous location set..
        if (sequenceId != null) {
            HashSet<String> hs = sd.ackPositions.get(sequenceId);
            if (hs != null) {
                hs.remove(subscriptionKey);
                if (hs.isEmpty()) {
                    HashSet<String> firstSet = sd.ackPositions.values().iterator().next();
                    sd.ackPositions.remove(sequenceId);

                    // Did we just empty out the first set in the
                    // ordered list of ack locations? Then it's time to
                    // delete some messages.
                    if (hs == firstSet) {

                        // Find all the entries that need to get deleted.
                        ArrayList<Entry<Long, MessageKeys>> deletes = new ArrayList<Entry<Long, MessageKeys>>();
                        for (Iterator<Entry<Long, MessageKeys>> iterator = sd.orderIndex.iterator(tx); iterator.hasNext();) {
                            Entry<Long, MessageKeys> entry = iterator.next();
                            if (entry.getKey().compareTo(sequenceId) <= 0) {
                                // We don't do the actually delete while we are
                                // iterating the BTree since
                                // iterating would fail.
                                deletes.add(entry);
                            }
                        }

                        // Do the actual deletes.
                        for (Entry<Long, MessageKeys> entry : deletes) {
                            sd.locationIndex.remove(tx, entry.getValue().location);
                            sd.messageIdIndex.remove(tx,entry.getValue().messageId);
                            sd.orderIndex.remove(tx,entry.getKey());
                        }
                    }
                }
            }
        }
    }

    private String key(KahaDestination destination) {
        return destination.getType().getNumber() + ":" + destination.getName();
    }

    // /////////////////////////////////////////////////////////////////
    // Transaction related implementation methods.
    // /////////////////////////////////////////////////////////////////
    protected final LinkedHashMap<TransactionId, ArrayList<Operation>> inflightTransactions = new LinkedHashMap<TransactionId, ArrayList<Operation>>();
    protected final LinkedHashMap<TransactionId, ArrayList<Operation>> preparedTransactions = new LinkedHashMap<TransactionId, ArrayList<Operation>>();
 
    private ArrayList<Operation> getInflightTx(KahaTransactionInfo info, Location location) {
        TransactionId key = key(info);
        ArrayList<Operation> tx = inflightTransactions.get(key);
        if (tx == null) {
            tx = new ArrayList<Operation>();
            inflightTransactions.put(key, tx);
        }
        return tx;
    }

    private TransactionId key(KahaTransactionInfo transactionInfo) {
        if (transactionInfo.hasLocalTransacitonId()) {
            KahaLocalTransactionId tx = transactionInfo.getLocalTransacitonId();
            LocalTransactionId rc = new LocalTransactionId();
            rc.setConnectionId(new ConnectionId(tx.getConnectionId()));
            rc.setValue(tx.getTransacitonId());
            return rc;
        } else {
            KahaXATransactionId tx = transactionInfo.getXaTransacitonId();
            XATransactionId rc = new XATransactionId();
            rc.setBranchQualifier(tx.getBranchQualifier().toByteArray());
            rc.setGlobalTransactionId(tx.getGlobalTransactionId().toByteArray());
            rc.setFormatId(tx.getFormatId());
            return rc;
        }
    }

    abstract class Operation {
        final Location location;

        public Operation(Location location) {
            this.location = location;
        }

        public Location getLocation() {
            return location;
        }

        abstract public void execute(Transaction tx) throws IOException;
    }

    class AddOpperation extends Operation {
        final KahaAddMessageCommand command;

        public AddOpperation(KahaAddMessageCommand command, Location location) {
            super(location);
            this.command = command;
        }

        public void execute(Transaction tx) throws IOException {
            upadateIndex(tx, command, location);
        }

        public KahaAddMessageCommand getCommand() {
            return command;
        }
    }

    class RemoveOpperation extends Operation {
        final KahaRemoveMessageCommand command;

        public RemoveOpperation(KahaRemoveMessageCommand command, Location location) {
            super(location);
            this.command = command;
        }

        public void execute(Transaction tx) throws IOException {
            updateIndex(tx, command, location);
        }

        public KahaRemoveMessageCommand getCommand() {
            return command;
        }
    }

    // /////////////////////////////////////////////////////////////////
    // Initialization related implementation methods.
    // /////////////////////////////////////////////////////////////////

    private PageFile createPageFile() {
        PageFile index = new PageFile(directory, "db");
        index.setEnableWriteThread(isEnableIndexWriteAsync());
        index.setWriteBatchSize(getIndexWriteBatchSize());
        return index;
    }

    private Journal createJournal() {
        Journal manager = new Journal();
        manager.setDirectory(directory);
        manager.setMaxFileLength(getJournalMaxFileLength());
        return manager;
    }

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public boolean isDeleteAllMessages() {
        return deleteAllMessages;
    }

    public void setDeleteAllMessages(boolean deleteAllMessages) {
        this.deleteAllMessages = deleteAllMessages;
    }
    
    public void setIndexWriteBatchSize(int setIndexWriteBatchSize) {
        this.setIndexWriteBatchSize = setIndexWriteBatchSize;
    }

    public int getIndexWriteBatchSize() {
        return setIndexWriteBatchSize;
    }
    
    public void setEnableIndexWriteAsync(boolean enableIndexWriteAsync) {
        this.enableIndexWriteAsync = enableIndexWriteAsync;
    }
    
    boolean isEnableIndexWriteAsync() {
        return enableIndexWriteAsync;
    }
    
    public boolean isEnableJournalDiskSyncs() {
        return enableJournalDiskSyncs;
    }

    public void setEnableJournalDiskSyncs(boolean syncWrites) {
        this.enableJournalDiskSyncs = syncWrites;
    }

    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    public void setCheckpointInterval(long checkpointInterval) {
        this.checkpointInterval = checkpointInterval;
    }

    public long getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(long cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public void setJournalMaxFileLength(int journalMaxFileLength) {
        this.journalMaxFileLength = journalMaxFileLength;
    }
    
    public int getJournalMaxFileLength() {
        return journalMaxFileLength;
    }
    
    public PageFile getPageFile() {
        if (pageFile == null) {
            pageFile = createPageFile();
        }
		return pageFile;
	}

	public Journal getJournal() {
        if (journal == null) {
            journal = createJournal();
        }
		return journal;
	}

    public boolean isFailIfDatabaseIsLocked() {
        return failIfDatabaseIsLocked;
    }

    public void setFailIfDatabaseIsLocked(boolean failIfDatabaseIsLocked) {
        this.failIfDatabaseIsLocked = failIfDatabaseIsLocked;
    }
}
