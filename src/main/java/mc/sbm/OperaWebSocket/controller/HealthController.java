//package mc.sbm.OperaWebSocket.controller;
//
//
//import mc.sbm.OperaWebSocket.client.OracleStreamingWebSocketClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/health")
//public class HealthController {
//
//    private final OracleStreamingWebSocketClient webSocketClient;
//
//    @Autowired
//    public HealthController(OracleStreamingWebSocketClient webSocketClient) {
//        this.webSocketClient = webSocketClient;
//    }
//
//    @GetMapping
//    public ResponseEntity<Map<String, Object>> health() {
//        Map<String, Object> status = new HashMap<>();
//        status.put("application", "Oracle Hospitality Streaming Consumer");
//        status.put("websocket_connected", webSocketClient.isConnected());
//        status.put("timestamp", System.currentTimeMillis());
//
//        return ResponseEntity.ok(status);
//    }
//}