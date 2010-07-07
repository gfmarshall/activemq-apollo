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
package org.apache.activemq.apollo.stomp.perf

import _root_.java.util.concurrent.TimeUnit
import _root_.org.apache.activemq.apollo.broker._
import _root_.org.apache.activemq.apollo.broker.perf._
import _root_.org.apache.activemq.apollo.stomp._

import _root_.org.apache.activemq.util.buffer._
import collection.mutable.{ListBuffer, HashMap}

import AsciiBuffer._
import Stomp._
import _root_.org.apache.activemq.apollo.stomp.StompFrame
import _root_.org.fusesource.hawtdispatch.ScalaDispatch._
import org.fusesource.hawtdispatch.BaseRetained

object StompBrokerPerfTest {
  def main(args:Array[String]) = {
    val test = new StompBrokerPerfTest();
    test.setUp
    test.benchmark_1_1_1
  }
}
class StompBrokerPerfTest extends BaseBrokerPerfTest {

    override def createProducer() =  new StompRemoteProducer()
    override def createConsumer() = new StompRemoteConsumer()
    override def getRemoteWireFormat() = "stomp"

}

class StompRemoteConsumer extends RemoteConsumer {

    def setupSubscription() = {
        val stompDestination = if( destination.getDomain() == Domain.QUEUE_DOMAIN ) {
            ascii("/queue/"+destination.getName().toString());
        } else {
            ascii("/topic/"+destination.getName().toString());
        }

        var frame = StompFrame(Stomp.Commands.CONNECT);
        transport.oneway(frame, null);

        var headers:List[(AsciiBuffer, AsciiBuffer)] = Nil
        headers ::= (Stomp.Headers.Subscribe.DESTINATION, stompDestination)
        headers ::= (Stomp.Headers.Subscribe.ID, ascii("stomp-sub-"+name))
        headers ::= (Stomp.Headers.Subscribe.ACK_MODE, Stomp.Headers.Subscribe.AckModeValues.AUTO)

        frame = StompFrame(Stomp.Commands.SUBSCRIBE, headers);
        transport.oneway(frame, null);
    }

    def onTransportCommand(command:Object) = {
      var frame = command.asInstanceOf[StompFrame]
      frame match {
        case StompFrame(Responses.CONNECTED, headers, _, _) =>
        case StompFrame(Responses.MESSAGE, headers, content, _) =>
          messageReceived();
        case StompFrame(Responses.ERROR, headers, content, _) =>
          onFailure(new Exception("Server reported an error: " + frame.content));
        case _ =>
          onFailure(new Exception("Unexpected stomp command: " + frame.action));
      }
    }

  protected def messageReceived() {
    if (thinkTime > 0) {
      transport.suspendRead
      dispatchQueue.dispatchAfter(thinkTime, TimeUnit.MILLISECONDS, ^ {
        consumerRate.increment();
        if (!stopped) {
          transport.resumeRead
        }
      })
    } else {
      consumerRate.increment();
    }
  }

}

class StompRemoteProducer extends RemoteProducer {

    var stompDestination:AsciiBuffer = null

    def send_next:Unit = {
      var headers: List[(AsciiBuffer, AsciiBuffer)] = Nil
      headers ::= (Stomp.Headers.Send.DESTINATION, stompDestination);
      if (property != null) {
          headers ::= (ascii(property), ascii(property));
      }
//    var p = this.priority;
//    if (priorityMod > 0) {
//        p = if ((counter % priorityMod) == 0) { 0 } else { priority }
//    }

      var content = ascii(createPayload());
      val frame = StompFrame(Stomp.Commands.SEND, headers, content)
      val delivery = new BaseRetained()
      delivery.setDisposer(^{
        rate.increment();
        val task = ^ {
          if( !stopped ) {
            send_next
          }
        }
        if( thinkTime > 0 ) {
          dispatchQueue.dispatchAfter(thinkTime, TimeUnit.MILLISECONDS, task)
        } else {
          dispatchQueue << task
        }
      })
      transport.oneway(frame, delivery)
      delivery.release
    }

    override def setupProducer() = {
      if( destination.getDomain() == Domain.QUEUE_DOMAIN  ) {
          stompDestination = ascii("/queue/"+destination.getName().toString());
      } else {
          stompDestination = ascii("/topic/"+destination.getName().toString());
      }
      transport.oneway(StompFrame(Stomp.Commands.CONNECT), null);
      send_next
    }

    def onTransportCommand(command:Object) = {
      var frame = command.asInstanceOf[StompFrame]
      frame match {
        case StompFrame(Responses.CONNECTED, headers, _, _) =>
        case StompFrame(Responses.ERROR, headers, content, _) =>
          onFailure(new Exception("Server reported an error: " + frame.content.utf8));
        case _ =>
          onFailure(new Exception("Unexpected stomp command: " + frame.action));
      }
    }

}
