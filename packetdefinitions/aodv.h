// Physical Layer
cc.freq = 868500000;
cc.sop  = 0x33;
cc.baud = 19200;
cc.crc  = 0xF1F1;

// network byte order: big endian
defaults.endianness = "big";

// alignment
defaults.alignment  = 1;

// basic message type
defaults.packet =    "bmac_msg_st";

/**
 * Packet Types
 */

const int CONTROL_TYPE  = 240; // ccc_type for AODV Ccontrol traffic
const int RREQ_TYPE     =   1;
const int RREP_TYPE     =   2;
const int RREP_ACK_TYPE =   3;
const int RERR_TYPE     =   4;

const int RFC_TYPE      =  23; // ccc_type for Routing packets
const int RFC_TYPE_DATA =   1; //Type for RFC data packets.
const int RFC_TYPE_ACK  =   2; //Type for RFC data acknowledgement packets.

/** types */
typedef uint32_t u_long;
typedef uint16_t u_short;
typedef uint8_t  u_char;


/** bmac header used by link layer */
typedef struct bmac_msg_st {
    u_short source; 
    u_short destination;    
    u_char length;
    u_char flags; 
    u_char data[length];
    u_short crc;
} bmac_msg_t;

/** chipcon communication header */
typedef struct ccc_packet_st : bmac_msg_st.data {
    u_short src;     ///< source of the packet
    u_short dst;     ///< destination of the packet
    u_short length;  ///< payload length
    u_char type;     ///< packet type
    u_char data[];   ///< payload data
} ccc_packet_t; 

/** AODV packet */
typedef struct aodv_packet_st : ccc_packet_t.data ( type == CONTROL_TYPE) {
    u_char aodv_type;     ///< Type of the packet is always RFC_DATA_TYPE, leave blank, this is set internally.
    u_char data[];        ///< payload data
} aodv_packet_t;

/** ROUTING packet */
typedef struct routing_packet_st : ccc_packet_t.data ( type == RFC_TYPE) {
    u_char routing_type;   ///< Type of the packet is always RFC_DATA_TYPE, leave blank, this is set internally.
    u_char data[];         ///< payload data
} routing_packet_t;

/**
 * Structure of an rfc  packet.
 * 
 * \warning Make sure that the data length field and the length of the actual data
 * are entered correctly.
 *
 * \warning Check the MTU with rfc_get_mtu(). Since RFC does not yet support fragmenting 
 * packets, this MUST be done at application level.
 */
typedef struct rfc_packet_st : routing_packet_t.data ( routing_type == RFC_TYPE_DATA) {
  u_char port;    ///< Port that the packet is directed to. 
  u_short seqno;  ///< Sequence number of the RFC Packet. The sequence number uniquely identifies any data packet.
  u_short flags;  ///< Flags: A == Use Ack for this packet.
  u_short src;    ///< Source that sent the packet.
  u_short dst;    ///< Destination to which the packet is targeted.
  u_short length; ///< Denotes the payload length in bytes.
  u_char data[];  ///< The payload in bytes. Payload should be delivered in network byte order.
} rfc_packet_t;

typedef struct rfc_ack_packet_st : routing_packet_t.data ( routing_type == RFC_TYPE_ACK) {
  u_short originator;   // Originator, who first sent the packet which must be acknowledged.
  u_short seqno;        // Sequence number of the packet that must be acknowledged.
} rfc_ack_packet_t;


/**
 * Structure of a RREQ packet
 */
typedef struct aodv_control_rreq_packet_st : aodv_packet_t.data ( aodv_type == RREQ_TYPE) {
  u_char flags;                ///< Flags (5 bit) + reserved (3 bit).
  u_char hopcount;             ///< Number of hops that RREQ made.
  u_char reserved;             ///< Reserved, not present in original AODV.
  u_short id;                  ///< RREQ id (16 bit, not 32 bit as in rfc3561).
  u_short destination_address; ///< Address of the destination node (16 bit, not 32 bit as in rfc3561).
  u_short dsn;                 ///< Last known sequence number of the destination (16 bit, not 32 bit as in rfc3561).
  u_short originator_address;  ///< Originatot address (16 bit, not 32 bit as in rfc3561).
  u_short osn;                 ///< Originator sequence number (16 bit, not 32 bit as in rfc3561).
  u_short ttl;                 ///< Time to life. Number of hops remaining until packet expires.
} aodv_control_rreq_packet_t;

/**
 * Structure of a RREP packet
 */
typedef struct aodv_control_rrep_packet_st : aodv_packet_t.data ( aodv_type == RREP_TYPE) {
  u_char flags;                ///< Flags (2 bit) + reserved (6 bit).
  u_char prefix_sz;            ///< Reserved (3 bit) + Prefix SZ (5 bit).
  u_char hopcount;             ///< Number of hops that RREP made.
  u_short destination_address; ///< Address of the destination node (16 bit, not 32 bit as in rfc3561).
  u_short dsn;                 ///< Last known sequence number of the destination (16 bit, not 32 bit as in rfc3561).
  u_short originator_address;  ///< Originator address (16 bit, not 32 bit as in rfc3561).
  u_long lifetime;             ///< Lifetime of the route.
  u_short ttl;                 ///< Time to life. Number of hops remaining until packet expires.
}  aodv_control_rrep_packet_t;

/**
 * Structure of an RERR packet
 */
typedef struct aodv_control_rerr_packet_st : aodv_packet_t.data ( aodv_type == RERR_TYPE) {
  u_short flags;                           ///< Flags (1 bit) + reserved (15 bit).
  u_char destcount;                        ///< Number of unreachable destiantions.
  u_short unreachable_destination_address; ///< Address of the unreachable destination (16 bit, not 32 bit as in rfc3561).
  u_short unreachable_dsn;                 ///< Last known sequence number of the unreachable destination
  u_short additional_unreachable_destination_address; ///< Additional unreachable destination addresses (if needed)
  u_short additional_unreachable_dsn;      ///< Last known additional unreachable destination sequence number.
} aodv_control_rerr_packet_t;



/**
 * Structure of an RREP-ACK packet
 */
typedef struct aodv_control_rrep_ack_packet_st : aodv_packet_t.data ( aodv_type == RREP_ACK_TYPE) {
  u_char reserved;                 ///< Reserved (8 bit).
} aodv_control_rrep_ack_packet_t;
