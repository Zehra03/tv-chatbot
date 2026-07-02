package com.paximum.paxassist.auth.service;

import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.dto.RegisterRequest;
import com.paximum.paxassist.auth.dto.UserResponse;
import com.paximum.paxassist.auth.exception.ConflictException;
import com.paximum.paxassist.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Bu e-posta adresi zaten kayıtlı");
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                "USER"
        );

        return UserResponse.from(userRepository.save(user));
    }
}
