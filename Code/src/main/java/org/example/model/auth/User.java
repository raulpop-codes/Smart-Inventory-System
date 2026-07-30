package org.example.model.auth;

import lombok.Getter;
import org.example.model.auth.exceptions.*;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

@Getter
public class User {
    private final String username;
    private final String password;
    private final String email;

    // Constructor used for rigorous checks
    public User(String username, String password, String email) throws InvalidLengthException, SpecialCharacterException, NumberException, InvalidEmailException, InvalidUsernameException {
        checkUsername(username);
        checkPassword(password);
        checkEmail(email);

        this.username = username;
        this.password = password;
        this.email = email;
    }

    // Private Constructor used only for reading from database
    private User(String username, String hashedPassword, String email, boolean isFromDb) {
        this.username = username;
        this.password = hashedPassword;
        this.email = email;
    }

    // Factory method - reading from database - ignores checks for hash password
    public static User fromDatabase(String username, String hashedPassword, String email) {
        return new User(username, hashedPassword, email, true);
    }


    public boolean checkUser(String inputUsername, String inputPassword) {
        if (!this.username.equals(inputUsername)) {
            return false;
        }

        return BCrypt.checkpw(inputPassword, this.password);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(username, user.username) && Objects.equals(password, user.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    // --- VALIDATING DATA BEFORE CREATION OF OBJECT ---
    private void checkPassword(String password) throws InvalidLengthException, SpecialCharacterException, NumberException {
        if (password == null || password.trim().isEmpty()) {
            throw new InvalidLengthException("Password cannot be empty!");
        }
        if(password.length() <= 8 || password.length() >= 15){
            throw new InvalidLengthException("Password must be between 8  and 15 characters!");
        }
        if(!password.matches(".*[!_\\-~].*")){
            throw new SpecialCharacterException("Password must contain at least one of the characters: !_-~");
        }
        if(!password.matches(".*[\\d].*")){
            throw new NumberException("Password must contain at least on number: 0-9");
        }
    }

    private void checkUsername(String username) throws InvalidLengthException, SpecialCharacterException, NumberException{
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidUsernameException("Username cannot be empty!");
        }
        if(username.length()<6 || username.length()>20){
            throw new InvalidLengthException("Username must be between 6 and 20 characters!");
        }
        if (!username.matches("^[a-zA-Z0-9]*$")){
            throw new SpecialCharacterException("Username must only contain alphanumeric characters!");
        }
    }

    private void checkEmail(String email) throws InvalidEmailException, InvalidLengthException{
        if (email == null || email.trim().isEmpty()) {
            throw new InvalidEmailException("Email cannot be empty!");
        }
        if (email.length() > 50) {
            throw new InvalidLengthException("Email must not exceed 50 characters!");
        }
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        if(!email.matches(emailRegex)){
            throw new InvalidEmailException("Invalid email format! Example: user@domain.com");
        }
    }
}

