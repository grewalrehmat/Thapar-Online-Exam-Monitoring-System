
package com.javalab.config;

import com.javalab.model.*;
import com.javalab.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seed(
            UserRepository users,
            LabRepository labs,
            PasswordEncoder encoder) {

        return args -> {

            // =================================================
            // TEACHER
            // =================================================

            var teacher =
                    users.findByEmail("teacher@javalab.local");

            if (teacher.isEmpty()) {

                users.save(new User(
                        "Demo Teacher",
                        "teacher@javalab.local",
                        null,
                        encoder.encode("teacher123"),
                        Role.TEACHER
                ));

                System.out.println(
                        "Created teacher account"
                );
            }


            // =================================================
            // EXISTING STUDENT 1
            // Convert old demo account if it exists.
            // =================================================

            var oldStudent1 =
                    users.findByEmail("student@javalab.local");

            var user1 =
                    users.findByEmail("user1@javalab.local");

            if (user1.isEmpty() && oldStudent1.isPresent()) {

                User student = oldStudent1.get();

                student.setName("User 1");
                student.setEmail("user1@javalab.local");
                student.setRollNumber("23BCS001");
                student.setPasswordHash(
                        encoder.encode("user123")
                );
                student.setRole(Role.STUDENT);

                users.save(student);

                System.out.println(
                        "Migrated old student account → User 1"
                );

            } else if (user1.isEmpty()) {

                users.save(new User(
                        "User 1",
                        "user1@javalab.local",
                        "23BCS001",
                        encoder.encode("user123"),
                        Role.STUDENT
                ));

                System.out.println(
                        "Created User 1"
                );
            }


            // =================================================
            // EXISTING STUDENT 2
            // Convert old demo account if it exists.
            // =================================================

            var oldStudent2 =
                    users.findByEmail("student2@javalab.local");

            var user2 =
                    users.findByEmail("user2@javalab.local");

            if (user2.isEmpty() && oldStudent2.isPresent()) {

                User student = oldStudent2.get();

                student.setName("User 2");
                student.setEmail("user2@javalab.local");
                student.setRollNumber("23BCS002");
                student.setPasswordHash(
                        encoder.encode("user123")
                );
                student.setRole(Role.STUDENT);

                users.save(student);

                System.out.println(
                        "Migrated old student2 account → User 2"
                );

            } else if (user2.isEmpty()) {

                users.save(new User(
                        "User 2",
                        "user2@javalab.local",
                        "23BCS002",
                        encoder.encode("user123"),
                        Role.STUDENT
                ));

                System.out.println(
                        "Created User 2"
                );
            }


            // =================================================
            // USERS 3 - 10
            // =================================================

            for (int i = 3; i <= 10; i++) {

                String email =
                        "user" + i + "@javalab.local";

                String roll =
                        "23BCS" +
                        String.format("%03d", i);

                var existing =
                        users.findByEmail(email);

                if (existing.isEmpty()) {

                    users.save(new User(
                            "User " + i,
                            email,
                            roll,
                            encoder.encode("user123"),
                            Role.STUDENT
                    ));

                    System.out.println(
                            "Created User " + i
                    );
                }
            }


            // =================================================
            // DEMO LAB
            // =================================================

            if (labs.count() == 0) {

                labs.save(new LabSession(

                        "Java OOP Evaluation",

                        "Write a Java program demonstrating " +
                        "inheritance and method overriding.",

                        LocalDateTime.now()
                                .minusMinutes(10),

                        LocalDateTime.now()
                                .plusHours(3),

                        LabStatus.ACTIVE,

                        true
                ));

                System.out.println(
                        "Created demo lab"
                );
            }


            System.out.println(
                    "JavaLab demo data initialization complete."
            );
        };
    }
}
