import jaydebeapi
import os

# Connect to H2 database
db_path = r"C:\Users\monik\OneDrive\Desktop\EIS\bakend\emailintelligence\data\emaildb"

conn = jaydebeapi.connect(
    "org.h2.Driver",
    f"jdbc:h2:{db_path}",
    ["sa", ""],
    r"C:\Users\monik\.m2\repository\com\h2database\h2\2.1.214\h2-2.1.214.jar"
)
cursor = conn.cursor()
cursor.execute("UPDATE emails SET summary = NULL, reply = NULL WHERE summary = 'Analysis unavailable' OR summary LIKE '%Gemini unavailable%' OR summary LIKE '%AI unavailable%'")
conn.commit()
print(f"Reset {cursor.rowcount} emails")
conn.close()
