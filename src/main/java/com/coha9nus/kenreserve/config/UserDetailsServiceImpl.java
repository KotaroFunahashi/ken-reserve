package com.coha9nus.kenreserve.config;

import com.coha9nus.kenreserve.domain.user.User;
import com.coha9nus.kenreserve.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + loginId));
        return new LoginUser(
                user.getId(),
                user.getLoginId(),
                user.getPassword(),
                user.getDisplayName(),
                user.getRole()
        );
    }
}
