package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.core.WorkflowConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@Import(WorkflowConfiguration.class)
@EntityScan(basePackageClasses = WorkflowConfiguration.class)
@EnableJpaRepositories(basePackageClasses = WorkflowConfiguration.class)
public class WorkflowTestHostApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowTestHostApplication.class, args);
    }

}
