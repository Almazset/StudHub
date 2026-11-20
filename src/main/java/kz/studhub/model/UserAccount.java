package kz.studhub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(min = 2, max = 80)
    @Column(nullable = false, length = 80)
    private String fullName;

    @Size(max = 32) @Column(length = 32)
    private String nickname;

    @Email @NotBlank @Column(nullable = false, unique = true, length = 120)
    private String email;

    @NotBlank @Size(min = 8, max = 100)
    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    private String theme = "dark";
    private String fontSize = "normal";
    private String language = "ru";
    private String profileVisibility = "private";
    @Column(nullable = false, length = 12, columnDefinition = "varchar(12) default 'members'")
    private String directMessages = "members";
    @Column(nullable = false, length = 12, columnDefinition = "varchar(12) default 'friends'")
    private String presenceVisibility = "friends";
    @Column(columnDefinition = "bytea")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private byte[] photo;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String twoFactorSecret;
    @Column(length = 2000)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String recoveryCodeHashes;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long lastOtpStep;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int otpFailures;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.time.Instant otpLockedUntil;

    public UserAccount() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getDisplayName() { return nickname == null || nickname.isBlank() ? fullName : nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getTheme() { return theme == null ? "dark" : theme; }
    public void setTheme(String value) { theme = value; }
    public String getFontSize() { return fontSize == null ? "normal" : fontSize; }
    public void setFontSize(String value) { fontSize = value; }
    public String getLanguage() { return language == null ? "ru" : language; }
    public void setLanguage(String value) { language = value; }
    public String getProfileVisibility() { return profileVisibility == null ? "private" : profileVisibility; }
    public void setProfileVisibility(String value) { profileVisibility = value; }
    public String getDirectMessages() { return directMessages == null ? "members" : directMessages; }
    public void setDirectMessages(String value) { directMessages = value; }
    public String getPresenceVisibility() { return presenceVisibility == null ? "friends" : presenceVisibility; }
    public void setPresenceVisibility(String value) { presenceVisibility = value; }
    public byte[] getPhoto() { return photo; }
    public void setPhoto(byte[] value) { photo = value; }
    public boolean isHasPhoto() { return photo != null && photo.length > 0; }
    public String getTwoFactorSecret() { return twoFactorSecret; }
    public void setTwoFactorSecret(String value) { twoFactorSecret = value; }
    public boolean isTwoFactorEnabled() { return twoFactorSecret != null; }
    public String getRecoveryCodeHashes() { return recoveryCodeHashes; }
    public void setRecoveryCodeHashes(String value) { recoveryCodeHashes = value; }
    public Long getLastOtpStep() { return lastOtpStep; }
    public void setLastOtpStep(Long value) { lastOtpStep = value; }
    public int getOtpFailures() { return otpFailures; }
    public void setOtpFailures(int value) { otpFailures = value; }
    public java.time.Instant getOtpLockedUntil() { return otpLockedUntil; }
    public void setOtpLockedUntil(java.time.Instant value) { otpLockedUntil = value; }
}
