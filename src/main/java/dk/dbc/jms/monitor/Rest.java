/*
 * Copyright (C) 2017 DBC A/S (http://dbc.dk/)
 *
 * This is part of dbc-jms-monitor
 *
 * dbc-jms-monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dbc-jms-monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.jms.monitor;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.inject.Inject;
import javax.jms.JMSConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.TextMessage;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.slf4j.LoggerFactory;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
@Path("/")
public class Rest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Rest.class);

    @Inject
    @JMSConnectionFactory("jms/JmsFactory")
    JMSContext jmsContext;

    /**
     * List age of first message in a queue in seconds.
     *
     * @param queueName name of target queue
     * @param filter    Message matching filter
     * @return a number (number)
     */
    @GET
    @Path("/age/{queueName:[a-zA-Z0-9_/ ]+}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response age(@PathParam("queueName") String queueName,
                        @QueryParam("filter") String filter) {
        try {
            QueueBrowser browser = createBrowser(queueName, filter);
            Enumeration enumeration = browser.getEnumeration();
            if (enumeration.hasMoreElements()) {
                Object elem = enumeration.nextElement();
                if (elem instanceof Message) {
                    Message message = (Message) elem;
                    long queued = message.getJMSTimestamp();
                    long age = ( System.currentTimeMillis() - queued ) / 1000;
                    return Response.ok(age).build();
                } else {
                    return Response.serverError().entity("Unknown message class: " + elem.getClass().getCanonicalName()).build();
                }
            } else {
                return Response.ok(0).build();
            }
        } catch (JMSException ex) {
            System.err.println(ex.getMessage());
            return Response.serverError().entity("Error: " + ex.getMessage()).build();
        }
    }

    /**
     * List content of messages as json (assuming content of messages is json.
     * No validation is done)
     *
     * @param queueName name of target queue
     * @param filter    Message matching filter
     * @param count     number of messages to show
     * @return json document
     */
    @GET
    @Path("/content/{queueName:[a-zA-Z0-9_/ ]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response content(@PathParam("queueName") String queueName,
                            @QueryParam("filter") String filter,
                            @QueryParam("count") @DefaultValue("100") Integer count) {
        try {
            QueueBrowser browser = createBrowser(queueName, filter);
            Enumeration enumeration = browser.getEnumeration();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            String objectPrefix = "";
            while (enumeration.hasMoreElements() && --count >= 0) {
                Object elem = enumeration.nextElement();
                if (elem instanceof Message) {
                    Message message = (Message) elem;
                    sb.append(objectPrefix)
                            .append(message.getBody(Object.class).toString());
                    objectPrefix = ",\n";
                } else {
                    return Response.serverError().entity("Unknown message class: " + elem.getClass().getCanonicalName()).build();
                }
            }
            sb.append("]");
            return Response.ok(sb.toString()).build();
        } catch (JMSException ex) {
            System.err.println(ex.getMessage());
            return Response.serverError().entity("Error: " + ex.getMessage()).build();
        }
    }

    /**
     * Create a jms browser
     *
     * @param queueName name of target queue
     * @param filter    Message matching filter
     * @return browser
     */
    private QueueBrowser createBrowser(String queueName, String filter) {
        Queue queue = jmsContext.createQueue(queueName);
        if (filter == null || filter.isEmpty()) {
            return jmsContext.createBrowser(queue);
        } else {
            return jmsContext.createBrowser(queue, filter);
        }
    }

    /**
     * Get request delegates to {@link #postNew(javax.ws.rs.core.MultivaluedMap)
     * }
     *
     * @param uriInfo information about the request
     * @return same as {@link #postNew(javax.ws.rs.core.MultivaluedMap) }
     */
    @GET
    @Path("/new")
    public Response getNew(@Context UriInfo uriInfo) {
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters(true);
        return postNew(params);
    }

    /**
     *
     * @param params request parameters (queue, json , (key, type, value)*)
     * @return "Ok" or exception message
     */
    @POST
    @Path("/new")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response postNew(MultivaluedMap<String, String> params) {
        try {
            Queue queue = jmsContext.createQueue(params.getFirst("queue"));
            JMSProducer producer = jmsContext.createProducer();
            TextMessage message = jmsContext.createTextMessage(params.getFirst("json"));
            List<String> keys = params.getOrDefault("key", Collections.EMPTY_LIST);
            List<String> types = params.getOrDefault("type", Collections.EMPTY_LIST);
            List<String> values = params.getOrDefault("value", Collections.EMPTY_LIST);
            int min = keys.size();
            min = Integer.min(min, types.size());
            min = Integer.min(min, values.size());
            for (int i = 0 ; i < min ; i++) {
                switch (types.get(i)) {
                    case "s":
                        message.setStringProperty(keys.get(i), values.get(i));
                        break;
                    case "i":
                        message.setIntProperty(keys.get(i), Integer.parseInt(values.get(i)));
                        break;
                    case "d":
                        message.setDoubleProperty(keys.get(i), Double.parseDouble(values.get(i)));
                        break;
                    case "b":
                        message.setBooleanProperty(keys.get(i), Boolean.parseBoolean(values.get(i)));
                        break;
                    default:
                        throw new IllegalStateException("Unknown type: " + types.get(i));
                }
            }
            String delay =  params.getFirst("delay");
            if(delay == null || delay.isEmpty())
                delay = "0";
            producer.setDeliveryDelay(Long.parseUnsignedLong(delay));
            producer.send(queue, message);
            return Response.ok("Ok").build();
        } catch (JMSException | RuntimeException ex) {
            log.error("Exception: " + ex.getMessage());
            log.debug("Exception:", ex);
            return Response.status(Response.Status.NOT_ACCEPTABLE).entity(ex.getMessage()).build();
        }
    }
}
