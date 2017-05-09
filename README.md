# jms-monitor
JavaEE application to monitor message queues

## Interfaces on main page

### Monitor

 * Show age in seconds of 1st message in the queue (0 if queue is empty)

 * Show content of messages (assuming content is text/json, no validation done)

 * Filter on message properties.

### Create

 * Create a message with optional properties

 * curl -v "http://localhost:8080/jms-monitor/api/new?queue=myqueue&json=`perl -MURI::Escape -pe 's/(.*)/uri_esca($1)/e;'`&key=foo&type=s&value=bar&key=num&type=i&value=10"