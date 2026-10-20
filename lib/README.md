# Microsoft JDBC Driver
JAR không được tải được trong môi trường tạo bản bàn giao (DNS mạng bị chặn).
Chạy scripts/fetch-driver.ps1 trên Windows hoặc scripts/fetch-driver.sh trên Linux để tải bản 13.4.0.jre11 từ Maven Central; hoặc để Maven/NetBeans lấy dependency trong pom.xml.
Driver là phụ thuộc chạy duy nhất. Build và kiểm thử logic không cần driver.
Nguồn chính thức: https://learn.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server
Kiểm tra chính sách giấy phép của Microsoft JDBC Driver trước khi phân phối lại.
