package br.com.mkfinance.infrastructure.security;

import br.com.mkfinance.infrastructure.persistence.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final MemberRepository memberRepository;
    private final WorkspaceContext workspaceContext;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new WorkspaceAccessInterceptor(memberRepository, workspaceContext))
                .addPathPatterns("/workspaces/*/**");
    }
}
