package org.musinsa.payments.point;

import org.musinsa.payments.point.domain.UserAccount;
import org.musinsa.payments.point.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PointApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointApplication.class, args);
    }

    /**
     * 애플리케이션 시작 시 초기 샘플 데이터 생성
     */
    @Bean
    public CommandLineRunner initData(UserAccountRepository userRepository) {
        return args -> {
            // yonkum 사용자 생성 (기본 보유 한도 100만P, 1회 적립 한도 10만P 설정)
            if (userRepository.findByUserId("yonkum").isEmpty()) {
                userRepository.save(UserAccount.builder()
                        .userId("yonkum")
                        .name("김원겸")
                        .maxAccumulationPoint(100000L)
                        .maxRetentionPoint(1000000L)
                        .totalPoint(0L)
                        .build());
                System.out.println("[DEBUG_LOG] 초기 사용자 'yonkum' 생성 완료");
            }
        };
    }
}
