package test_Evaluation_machine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

//@SpringBootApplication
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class OperationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperationSystemApplication.class, args);
    }

}
