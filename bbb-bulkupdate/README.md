# bbb-bulkupdate

`bbb-bulkupdate` is a command-line application that reads CSV files exported by data pipelines and performs fast bulk updates into a target database (MySQL, MariaDB, PostgreSQL, or SQLite).

## Features

- **Multi-Database Support**: Uses native high-performance bulk loading mechanisms tailored for each target database:
  - **MySQL / MariaDB**: `LOAD DATA LOCAL INFILE`
  - **PostgreSQL**: Streaming CSV using PostgreSQL `CopyManager`
  - **SQLite**: Batched prepared statement inserts with `WAL` journal mode and disabled synchronisation for fast throughput
- **Foreign Key Safety**: Disables foreign key checks / triggers during the bulk load and safely re-enables them in a `finally` block.
- **Transaction Rollback**: Rolls back changes if any table bulk load fails.
- **Flexible Path Resolution**: Discovers `.csv` files matching table names in either the specified CSV folder or a database-type subfolder (e.g. `csv/mysql`, `csv/sqlite`).

## Command-Line Arguments

| Short | Long | Description | Required | Default |
|---|---|---|---|---|
| `-c` | `--connectionString` | JDBC connection string | Yes | |
| `-u` | `--userName` | Database username | No | `""` |
| `-p` | `--password` | Database password | No | `""` |
| `-bd` | `--baseDirectory` | Base directory for data files | Yes | |
| `-cd` | `--csvDir` | Directory containing CSV files (relative to baseDirectory or absolute) | Yes | |
| `-dt` | `--dbType` | Target database type (`mysql`, `mariadb`, `postgresql`, `sqlite`) | No | `mysql` |
| `-h` | `--help` | Display help information | No | |

## Usage Examples

### SQLite

```bash
./gradlew :bbb-bulkupdate:run --args="-c jdbc:sqlite:/path/to/ballbyball.db -bd /data/cricket -cd csv -dt sqlite"
```

### PostgreSQL

```bash
./gradlew :bbb-bulkupdate:run --args="-c jdbc:postgresql://localhost:5432/ballbyball -u postgres -p secret -bd /data/cricket -cd csv -dt postgresql"
```

### MySQL / MariaDB

```bash
./gradlew :bbb-bulkupdate:run --args="-c jdbc:mariadb://localhost:3306/ballbyball?allowLocalInfile=true -u root -p secret -bd /data/cricket -cd csv -dt mariadb"
```
