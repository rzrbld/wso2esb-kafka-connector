/*
 *  Copyright (c) 2005-2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.connector;


import java.util.Iterator;
import java.util.List;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axis2.AxisFault;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.ConnectException;

/**
 * Produce the messages to the kafka brokers
 */

public class KafkaProduce extends AbstractConnector {

    /**
     * namespace variable
     * @param messageContext
     * @throws org.wso2.carbon.connector.core.ConnectException
     */
    public void connect(MessageContext messageContext) throws ConnectException {

        SynapseLog log = getLog(messageContext);
        log.auditLog("SEND : send message to  Broker lists");
        //Get the producer with the configuration
        Producer<String, String> producer = KafkaUtils.getProducer(messageContext);
        String topic = this.getTopic(messageContext);
        String key = this.getKey(messageContext);
        String autoiterator = this.getAutoiterator(messageContext);
        String messageXpath = this.getMessageXpath(messageContext);
        String namespaces = this.getNamespaces(messageContext);
        
        try {
            if (producer != null) {
                if("on".equals(autoiterator)){
                    log.auditLog("SEND : use build-in iterator");
                    
                    if(messageXpath!=null){
                        OMElement message = (OMElement) this.getRawMessage(messageContext);
                        this.iterateChilds(message,producer,topic,key,messageXpath,namespaces,messageContext);
                    }else{
                        SOAPBody message = this.getRawMessage(messageContext);
                        this.simpleIterate(message,producer,topic,key,messageContext);
                    }
                }else{
                    log.auditLog("SEND : use single message send");
                    String message = this.getMessage(messageContext);
                    send(producer, topic, key, message);
                }
            } else {
                log.error("The producer not created");
            }
        } catch (Exception e) {
            log.error("Kafka producer connector : Error sending the message to broker lists ");
            throw new ConnectException(e);
        } finally {
            //Close the producer pool connections to all kafka brokers.Also closes the zookeeper client connection if any
            if (producer != null) {
                producer.close();
            }
        }
    }

    /**
     * Read the topic from the parameter
     */
    private String getTopic(MessageContext messageContext) {
        return KafkaUtils.lookupTemplateParameter(messageContext, KafkaConnectConstants.PARAM_TOPIC);
    }
    
    /**
     * Read the topic message iterator XPath string from the parameter
     */
    private String getMessageXpath(MessageContext messageContext) {
        return KafkaUtils.lookupTemplateParameter(messageContext, KafkaConnectConstants.PARAM_MESSAGE_XPATH);
    }
    
    /**
     * Read the autoiterator from the parameter
     */
    private String getAutoiterator(MessageContext messageContext) {
        return KafkaUtils.lookupTemplateParameter(messageContext, KafkaConnectConstants.PARAM_AUTOITERATE);
    }
    
    /**
     * Read the namespaces from the parameter
     */
    private String getNamespaces(MessageContext messageContext){
        return KafkaUtils.lookupTemplateParameter(messageContext, KafkaConnectConstants.PARAM_NAMESPACES);
    }   

    /**
     * Read the key from the parameter
     */
    private String getKey(MessageContext messageContext) {
        return KafkaUtils.lookupTemplateParameter(messageContext, KafkaConnectConstants.PARAM_KEY);
    }
    
    void iterateChilds(OMElement result, Producer<String, String> producer, String topic, String key, String messageXpath, String namespaces, MessageContext messageContext) throws Exception {
        AXIOMXPath xPath = new AXIOMXPath(messageXpath);
        
        if(namespaces!=null){
            String[] namespacesArray = namespaces.split(";");
            for (String ns : namespacesArray) {
                String[] nsKeyValue = ns.split("::");
                try{
                    xPath.addNamespace(nsKeyValue[0], nsKeyValue[1]);
                }catch (Exception e) {
                    throw new Exception("Error while adding namespaces. check namespaces parameter: " + e);
                }
            }
        }
        
        List selectedNodes;
        try{
            selectedNodes = xPath.selectNodes(result);
        }catch (Exception e) {
            throw new Exception("Error while apply xpath against message. check xpath and namespaces : " + e);
        }
        
        if (selectedNodes == null) {
            throw new Exception("Unexpected response : " + result);
        }
        
        Iterator iter = selectedNodes.iterator();
        
        while (iter.hasNext()) {
           
            OMElement childElem = (OMElement) iter.next();
            String message = childElem.toString();
            try{
                producer.send(new KeyedMessage<String, String>(topic, message));
            } 
            catch (Exception e) {
                throw new ConnectException(e);
            }
        }
        
    }

    /**
     * Get the messages from the message context and format the messages
     */
    private String getMessage(MessageContext messageContext) throws AxisFault {
        Axis2MessageContext axisMsgContext = (Axis2MessageContext) messageContext;
        org.apache.axis2.context.MessageContext msgContext = axisMsgContext.getAxis2MessageContext();
        return KafkaUtils.formatMessage(msgContext);
    }
    
    /**
     * Get the raw messages from the message context
     */
    private SOAPBody getRawMessage(MessageContext messageContext) throws AxisFault {
        Axis2MessageContext axisMsgContext = (Axis2MessageContext) messageContext;
        org.apache.axis2.context.MessageContext msgContext = axisMsgContext.getAxis2MessageContext();
        return msgContext.getEnvelope().getBody();
    }

    /**
     * Send the messages to the kafka broker with topic and the key that is optional
     */
    private void send(Producer<String, String> producer, String topic, String key, String message) {
        if (key == null) {
            producer.send(new KeyedMessage<String, String>(topic, message));
        } else {
            producer.send(new KeyedMessage<String, String>(topic, key, message));
        }
    }

    private void simpleIterate(SOAPBody body, Producer<String, String> producer, String topic, String key, MessageContext messageContext) throws ConnectException {
        for (Iterator itr = body.getFirstElement().getChildElements(); itr.hasNext();) {
            
            OMElement childElem = (OMElement) itr.next();
            String message = childElem.toString();
            try{
                producer.send(new KeyedMessage<String, String>(topic, message));
            } 
            catch (Exception e) {
                throw new ConnectException(e);
            }
        }
    }
    
}