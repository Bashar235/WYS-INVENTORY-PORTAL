package com.wys.inventory.service;

import com.wys.inventory.model.Role;
import com.wys.inventory.model.User;
import com.wys.inventory.repository.RoleRepository;
import com.wys.inventory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;

    /**
     * Creates a user with the given role code. The role must exist in the DB.
     * @param roleCode e.g. "ADMIN", "MANAGER", "SUPER_ADMIN"
     */
    public User createUser(String username, String password, String roleCode) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }
        Role role = roleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));
        User user = new User(username, passwordEncoder.encode(password), role);
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public boolean validateUser(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        return userOpt.map(user -> passwordEncoder.matches(password, user.getPassword())).orElse(false);
    }

    public User getUserByUsername(String username) {
        return findByUsername(username);
    }
}
