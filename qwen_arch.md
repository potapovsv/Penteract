Penteract Mondrian Architecture Document
Overview
Penteract Mondrian is an Open Source Online Analytical Processing (OLAP) Server, which implements the MDX (Multidimensional Expressions) query language. It provides XML for Analysis (XML/A) support for OLAP connectivity and acts as an OLAP server for Business Intelligence (BI) applications.

Project Structure
├── mondrian/                    # Main project directory
│   ├── demo/                   # Example applications and demos
│   ├── mondrian/               # Core Mondrian library source code
│   ├── SimpleTest/             # Test projects
│   ├── pom.xml                 # Main Maven build configuration
│   └── README.md               # Project documentation
Core Source Code Structure
src/main/java/mondrian/
├── calc/                       # Calculation and evaluation components
├── core/                       # Core utility classes
├── i18n/                       # Internationalization support
├── mdx/                        # MDX query parsing and processing
├── olap/                       # Core OLAP API and interfaces
├── olap4j/                     # OLAP4J connectivity implementation
├── parser/                     # Query parsing components
├── recorder/                   # Query recording functionality
├── resource/                   # Resource and property management
├── rolap/                      # ROLAP (Relational OLAP) implementation
├── server/                     # Server components and session management
├── spi/                        # Service Provider Interface
├── tui/                        # Text User Interface components
├── udf/                        # User Defined Functions
├── util/                       # Utility classes
├── web/                        # Web components
└── xmla/                       # XML for Analysis (XML/A) implementation
Key Architecture Components
1. XML/A Implementation Layer
The XML/A (XML for Analysis) layer provides a standard interface for OLAP services. Key components:

XmlaServlet: Base servlet class that handles XML/A requests and responses
DefaultXmlaServlet: Default implementation of the XML/A servlet that manages SOAP message processing
XmlaHandler: Processes XML/A discover and execute requests
XmlaRequest/XmlaResponse: Request/response interfaces for XML/A operations
Key Features:
Supports both SOAP and JSON response formats
Session management with BeginSession/Session/EndSession headers
Authentication support with security headers
Error handling with proper XML/A fault responses
2. ROLAP (Relational OLAP) Engine
The core computational engine that executes MDX queries against relational databases:

RolapConnection: ROLAP-specific implementation of OLAP connections
RolapSchema: Manages schema definitions and metadata
RolapCube: Represents OLAP cubes with dimensions and measures
RolapHierarchy/Level/Member: Multidimensional data structures
RolapResult: Query execution results
RolapAggregationManager: Handles data aggregation and caching
3. OLAP Core API
Abstract interfaces defining the OLAP operations:

Connection: Core connection interface
Cube: Cube abstraction for multidimensional data
Dimension/Hierarchy/Level/Member: Multidimensional data structures
Query/Result: Query execution interfaces
MondrianServer: Server instance management
4. Server Infrastructure
Components responsible for server-side functionality:

Session: Manages user sessions and authentication
Execution: Query execution tracking and monitoring
Repository: Schema/content repository management
Locus: Execution context management
Statement: SQL statement execution
5. Query Processing Pipeline
MDX Query → Parser → Validator → Evaluator → Result → XML/A Response
Parser: MDX query parsing (uses JavaCC for grammar)
Validator: Validates query structure and references
Evaluator: Executes and evaluates query expressions
Result: Manages query results and formatting
Key Dependencies and Technologies
Build System
Maven: Project build and dependency management
Java 25: Target Java version (with preview features enabled)
Jakarta EE: Servlet API and other EE specifications
Core Dependencies
olap4j: Standard OLAP API implementation
commons-*: Apache Commons utilities
log4j2: Logging framework
dom4j/xerces: XML processing
mysql-connector-j: Database connectivity
HikariCP: Connection pooling
lettuce: Redis client (for caching)
XML/A Dependencies
Jakarta Servlet API: HTTP servlet implementation
XML Processing: DOM/SAX parsers for SOAP processing
Configuration and Properties
Key Configuration Files:
mondrian.properties: Main configuration properties
pom.xml: Build configuration with Java 25 target
DataSourcesConfig.xml: Data source configuration for XML/A
Key Properties:
Memory and cache management
Query execution limits
Connection pooling settings
XML/A service configuration
Security Model
Role-based access control
Session management with authentication
XML/A security headers processing
Connection credential management
Performance Features
Query result caching
Aggregation optimization
Connection pooling
Memory-efficient result processing
Support for large result sets with chunked processing
Integration Points
XML/A Protocol: Standard OLAP connectivity interface
JDBC Connectivity: Relational database access
OLAP4J: Standard OLAP API compliance
Web Application Integration: Servlet container deployment
Architecture Considerations
Java 25 Migration: Project specifically targets Java 25 with preview features
Scalability: Designed for enterprise OLAP workloads
Standards Compliance: Follows XML/A and OLAP4J standards
Extensibility: SPI-based architecture for custom implementations
I’ve completed the analysis of the Penteract Mondrian codebase and provided the architecture document in our conversation. The document covers all the major architectural components of the system, including the XML/A implementation, ROLAP engine, OLAP API, and server infrastructure