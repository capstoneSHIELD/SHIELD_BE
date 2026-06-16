package org.example.shield.experiment.lawyermatch;

import org.example.shield.experiment.controller.ExperimentLawyerMatchController;
import org.example.shield.lawyer.application.LawyerEmbeddingTextBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentLawyerMatchProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void production_profile_does_not_register_experiment_lawyer_match_beans() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ExperimentLawyerMatchController.class);
                    assertThat(context).doesNotHaveBean(ExperimentLawyerMatchService.class);
                    assertThat(context).doesNotHaveBean(ExperimentLawyerCorpusStore.class);
                });
    }

    @Test
    void local_profile_registers_experiment_lawyer_match_beans() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExperimentLawyerMatchController.class);
                    assertThat(context).hasSingleBean(ExperimentLawyerMatchService.class);
                    assertThat(context).hasSingleBean(ExperimentLawyerCorpusStore.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            ExperimentLawyerMatchController.class,
            ExperimentLawyerMatchService.class,
            ExperimentLawyerCorpusStore.class,
            LawyerEmbeddingTextBuilder.class
    })
    static class TestConfig {
    }
}
