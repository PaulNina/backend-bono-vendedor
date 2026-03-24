package com.ninabit.bono.modules.auth;

import com.ninabit.bono.modules.auth.dto.AuthResponse;
import com.ninabit.bono.modules.auth.dto.LoginRequest;
import com.ninabit.bono.modules.auth.dto.RegisterRequest;
import com.ninabit.bono.modules.configuracion.ConfiguracionGlobalRepository;
import com.ninabit.bono.modules.tienda.Tienda;
import com.ninabit.bono.modules.tienda.TiendaRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import com.ninabit.bono.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

        private final AuthenticationManager authenticationManager;
        private final UserDetailsService userDetailsService;
        private final JwtUtil jwtUtil;
        private final UsuarioRepository usuarioRepository;
        private final VendedorRepository vendedorRepository;
        private final TiendaRepository tiendaRepository;
        private final PasswordEncoder passwordEncoder;
        private final com.ninabit.bono.modules.email.EmailService emailService;
        private final ConfiguracionGlobalRepository configuracionGlobalRepository;

        @org.springframework.beans.factory.annotation.Value("${app.frontend.url:https://bono.contabol.com}")
        private String frontendUrl;

        public AuthResponse login(LoginRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
                Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(request.getEmail())
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                String token = jwtUtil.generateToken(userDetails, usuario.getRol().name(), usuario.getId());

                return new AuthResponse(
                                token,
                                usuario.getRol().name(),
                                usuario.getNombreCompleto(),
                                usuario.getId(),
                                usuario.getVendedorId());
        }

        @Transactional
        public java.util.Map<String, String> register(RegisterRequest request) {
                if (usuarioRepository.existsByEmail(request.getEmail())) {
                        throw new RuntimeException("El correo ya está registrado.");
                }

                // Check auto-approve config
                boolean autoAprobar = configuracionGlobalRepository.findById("auto_aprobar_vendedores")
                                .map(c -> "true".equalsIgnoreCase(c.getValor()))
                                .orElse(false);

                // Look up tienda if provided
                Tienda tienda = null;
                if (request.getTiendaId() != null) {
                        tienda = tiendaRepository.findById(request.getTiendaId()).orElse(null);
                }

                // Create vendor record
                Vendedor vendedor = Vendedor.builder()
                                .nombreCompleto(request.getNombreCompleto())
                                .email(request.getEmail())
                                .telefono(request.getTelefono())
                                .tienda(tienda)
                                .activo(autoAprobar)
                                .pendingApproval(!autoAprobar)
                                .build();
                vendedor = vendedorRepository.save(vendedor);

                // Create user account
                Usuario usuario = Usuario.builder()
                                .email(request.getEmail())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .rol(Usuario.Rol.VENDOR)
                                .nombreCompleto(request.getNombreCompleto())
                                .vendedorId(vendedor.getId())
                                .activo(autoAprobar)
                                .build();
                usuario = usuarioRepository.save(usuario);

                String mensaje = autoAprobar
                                ? "Registro exitoso. Ya puedes iniciar sesión."
                                : "Registro exitoso. Espere la aprobación del administrador.";
                return java.util.Map.of("message", mensaje);
        }

        @Transactional
        public java.util.Map<String, String> forgotPassword(String email) {
                Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                                .orElseThrow(() -> new RuntimeException(
                                                "No se encontró un usuario activo con ese correo"));

                String token = java.util.UUID.randomUUID().toString();
                usuario.setResetPasswordToken(token);
                usuario.setResetPasswordTokenExpiry(java.time.LocalDateTime.now().plusMinutes(15));
                usuarioRepository.save(usuario);

                String resetUrl = frontendUrl + "/reset-password?token=" + token;
                String mensaje = "Hola " + usuario.getNombreCompleto() + ",\n\n"
                                + "Has solicitado restablecer tu contraseña. Ingresa al siguiente enlace para crear una nueva (tienes 15 minutos):\n\n"
                                + resetUrl + "\n\n"
                                + "Si no solicitaste esto, puedes ignorar este correo.\n\n"
                                + "Equipo Skyworth";

                emailService.sendEmail(usuario.getEmail(), "Recuperación de Contraseña - Skyworth", mensaje);

                return java.util.Map.of("message", "Se han enviado las instrucciones a tu correo electrónico.");
        }

        @Transactional
        public java.util.Map<String, String> resetPassword(String token, String newPassword) {
                Usuario usuario = usuarioRepository.findAll().stream()
                                .filter(u -> token.equals(u.getResetPasswordToken()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Token inválido o expirado."));

                if (usuario.getResetPasswordTokenExpiry() != null &&
                                usuario.getResetPasswordTokenExpiry().isBefore(java.time.LocalDateTime.now())) {
                        throw new RuntimeException("El token ha expirado. Por favor, solicita uno nuevo.");
                }

                usuario.setPassword(passwordEncoder.encode(newPassword));
                usuario.setResetPasswordToken(null);
                usuario.setResetPasswordTokenExpiry(null);
                usuarioRepository.save(usuario);

                return java.util.Map.of("message", "Contraseña restablecida exitosamente. Ya puedes iniciar sesión.");
        }
}
