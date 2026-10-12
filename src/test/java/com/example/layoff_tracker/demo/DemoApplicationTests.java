package com.example.layoff_tracker.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

@SpringBootTest(properties = {"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", "DB_PASSWORD=test"})
class DemoApplicationTests {
    @MockitoBean org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean Firestore firestore;
    @MockitoBean FirebaseApp firebaseApp;
    @MockitoBean FirebaseAuth firebaseAuth;

	@Test
	void contextLoads() {
	}

}
