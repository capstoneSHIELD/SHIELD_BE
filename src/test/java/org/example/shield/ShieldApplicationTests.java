package org.example.shield;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ShieldApplicationTests {

    @MockitoBean
    private FirebaseApp firebaseApp;

    @MockitoBean
    private FirebaseMessaging firebaseMessaging;

    @Test
    void contextLoads() {
    }

}
