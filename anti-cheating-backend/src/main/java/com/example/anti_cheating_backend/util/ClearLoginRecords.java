package com.example.anti_cheating_backend.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Utility to clear all login records from the database
 * This clears:
 * - identity_session (login sessions)
 * - identity_attempt (login attempts)
 * - identity_enrollment_session (enrollment sessions)
 */
public class ClearLoginRecords {
    
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/anti_cheating_db";
        String user = "root";
        String password = "";
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            Statement stmt = conn.createStatement();
            
            System.out.println("=====================================================");
            System.out.println("  Clear Previous Login Records");
            System.out.println("=====================================================");
            
            System.out.println("\nClearing identity_attempt...");
            int count1 = stmt.executeUpdate("DELETE FROM identity_attempt");
            System.out.println("  ✓ Deleted " + count1 + " records");
            
            System.out.println("\nClearing identity_enrollment_session...");
            int count2 = stmt.executeUpdate("DELETE FROM identity_enrollment_session");
            System.out.println("  ✓ Deleted " + count2 + " records");
            
            System.out.println("\nClearing identity_session...");
            int count3 = stmt.executeUpdate("DELETE FROM identity_session");
            System.out.println("  ✓ Deleted " + count3 + " records");
            
            System.out.println("\n=====================================================");
            System.out.println("  [COMPLETE] All previous login records cleared");
            System.out.println("  Total records deleted: " + (count1 + count2 + count3));
            System.out.println("=====================================================");
            
            stmt.close();
            conn.close();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
