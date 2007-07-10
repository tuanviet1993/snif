// Physical Layer
cc.freq = 868500000;
cc.sop = 0x33;
cc.baud = 19200;
cc.crc = 0xF1F1;

// endianess
defaults.endianness = "big";

// alignment
defaults.alignment  = 1;

// basic message type
defaults.packet =    "bmac_msg_st";

// Constants: Packet Types
const int BEACON_TYPE   = 1;
const int ADVERT_TYPE   = 2;
const int DISTANCE_TYPE = 3;
const int DATA_TYPE     = 4;
/** nr neighbours in advert packet */
const int NEIGHBOR_NUMBER = 4;

/** types */
typedef uint16_t u_short;
typedef uint8_t  u_char;

struct neighbour_entry {
	u_short node_id;
	u_char  quality;
};

/** bmac header used by link layer */
struct bmac_msg_st {
    /** */
    u_short source;
    /* */
    u_short destination;    
    /* */
    u_char length;
    /* */
    u_char flags; 
    /* */
    u_char data[length];
    /* */
    u_short crc;
};

/** chipcon communication header */
struct ccc_packet_st : bmac_msg_st.data {
    /** source of the packet */
    u_short src;
    /** destination of the packet */
    u_short dst; 
    /** payload length */
    u_short length;
    /** packet type */
    u_char type;
    /** payload data */
    u_char data[];
};

struct beacon_packet : ccc_packet_st.data ( type == BEACON_TYPE) {
	u_short node_id;
	u_short seq_nr;
	u_short battery;    // debug only
	u_char  appversion; // debug only
}
;

struct advert_packet : ccc_packet_st.data ( type == ADVERT_TYPE) {
	u_short node_id;
	struct neighbour_entry neighbours[NEIGHBOR_NUMBER];
};

struct distance_packet : ccc_packet_st.data ( type == DISTANCE_TYPE) {
	u_short node_id;
	u_short sink_id;
	u_short distance;
	u_char  round_nr;
};

struct data_packet : ccc_packet_st.data ( type == DATA_TYPE) {
	u_short node_id;
	u_short seq_nr;
	u_char  temp;
};
