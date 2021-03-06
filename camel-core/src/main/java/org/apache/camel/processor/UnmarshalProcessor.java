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
package org.apache.camel.processor;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Traceable;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Unmarshals the body of the incoming message using the given
 * <a href="http://camel.apache.org/data-format.html">data format</a>
 *
 * @version 
 */
public class UnmarshalProcessor extends ServiceSupport implements Processor, Traceable, CamelContextAware {
    private CamelContext camelContext;
    private final DataFormat dataFormat;

    public UnmarshalProcessor(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    public void process(Exchange exchange) throws Exception {
        ObjectHelper.notNull(dataFormat, "dataFormat");

        InputStream stream = exchange.getIn().getMandatoryBody(InputStream.class);
        try {
            // lets setup the out message before we invoke the dataFormat so that it can mutate it if necessary
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());

            Object result = dataFormat.unmarshal(exchange, stream);
            if (result instanceof Exchange) {
                if (result != exchange) {
                    // it's not allowed to return another exchange other than the one provided to dataFormat
                    throw new RuntimeCamelException("The returned exchange " + result + " is not the same as " + exchange + " provided to the DataFormat");
                }
            } else if (result instanceof Message) {
                // the dataformat has probably set headers, attachments, etc. so let's use it as the outbound payload
                exchange.setOut((Message) result);
            } else {
                out.setBody(result);
            }
        } catch (Exception e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        } finally {
            IOHelper.close(stream, "input stream");
        }
    }

    public String toString() {
        return "Unmarshal[" + dataFormat + "]";
    }

    public String getTraceLabel() {
        return "unmarshal[" + dataFormat + "]";
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        // inject CamelContext on data format
        if (dataFormat instanceof CamelContextAware) {
            ((CamelContextAware) dataFormat).setCamelContext(camelContext);
        }
        ServiceHelper.startService(dataFormat);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(dataFormat);
    }

}