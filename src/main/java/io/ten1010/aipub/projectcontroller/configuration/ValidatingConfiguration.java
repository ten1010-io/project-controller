package io.ten1010.aipub.projectcontroller.configuration;

import io.ten1010.aipub.projectcontroller.mutating.service.ReviewHandler;
import io.ten1010.aipub.projectcontroller.validating.service.UserLabelValidateHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidatingConfiguration {

  @Bean
  @Qualifier("validateHandlers")
  public List<ReviewHandler> validateHandlers() {
    UserLabelValidateHandler userLabelValidateHandler = new UserLabelValidateHandler();
    return List.of(userLabelValidateHandler);
  }

}
