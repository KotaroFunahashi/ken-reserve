package com.coha9nus.kenreserve.config;

import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginId));
        return new org.springframework.security.core.userdetails.User(user.getLoginId(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
