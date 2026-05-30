package com.nyle.kra.revenue.security;

import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.identity.AuthCredential;
import com.nyle.kra.revenue.identity.AuthCredentialRepository;
import com.nyle.kra.revenue.identity.Role;
import com.nyle.kra.revenue.identity.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultAdminSeeder implements ApplicationRunner {

    private final SecurityProperties securityProperties;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminSeeder(
            SecurityProperties securityProperties,
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            AuthCredentialRepository authCredentialRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.securityProperties = securityProperties;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role seed is missing"));

        AppUser admin = appUserRepository.findByEmailIgnoreCase(securityProperties.defaultAdminEmail())
                .orElseGet(() -> new AppUser(
                        securityProperties.defaultAdminEmail(),
                        "Phase Two Administrator",
                        "Platform Engineering",
                        "ACTIVE"
                ));

        admin.addRole(adminRole);
        AppUser savedAdmin = appUserRepository.save(admin);

        if (!authCredentialRepository.existsById(savedAdmin.getId())) {
            authCredentialRepository.save(new AuthCredential(
                    savedAdmin,
                    passwordEncoder.encode(securityProperties.defaultAdminPassword())
            ));
        }
    }
}
