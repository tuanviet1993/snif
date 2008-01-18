/** minimal test packets for unit tests */

// network byte order: big endian
defaults.endianness = "big";

// alignment
defaults.alignment  = 1;

// basic message type
defaults.packet =    "basic";

struct basic {
	uint8_t  count;
	uint16_t array[count];
	uint16_t crc;
};


/**
 * this is not supported yet, 2 x variable size arrays 
 * note: MAC layers will at most use one "length" field on the physical layer */
struct doubleArray {
	uint8_t  countA;
	uint16_t arrayA[countA];
	uint8_t  countB;
	uint16_t arrayB[countB];
	uint16_t crc;
};