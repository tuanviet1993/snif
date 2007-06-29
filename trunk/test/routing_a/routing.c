/*
 * Copyright (C) 2000-2006 by ETH Zurich
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY ETH ZURICH AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL ETH ZURICH
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * For additional information see http://www.btnode.ethz.ch/
 *
 * $ routing.c 2006/10/23 20:57:42 mcortesi $
 * 
 */

/** 
 * buggy simple data collection application 
 * 
 * main problem: if route announcements are not heared, a child node is selected
 * as new parent and routing loops occure. As there is not loop suppression the
 * packet sent between two nodes repeatedly without pause. This cloaks the otherwise
 * low data rate leading to collisions and missed packets which causes more path 
 * announcements to be lost
 */

#include <sys/timer.h>
#include <led/btn-led.h>
#include <hardware/btn-hardware.h>
#include <hardware/btn-bat.h>
#include <cc/bmac.h>
#include <cc/ccc.h>
#include <cc/cc1000.h>
#include <eepromdb/btn-eepromdb.h>
#include <btsense/btsense.h>
#include "routing.h"

#include "program_version.h"

#define APP_VERSION 3

// bt for MAC ID
#include <dev/usartavr.h>
#include <bt/bt_hci_api.h>


#define NEIGHBOR_NUMBER 8

#define BEACON_TYPE 0x01
#define ADVERT_TYPE 0x02
#define DISTANCE_TYPE 0x03
#define DATA_TYPE 0x04
#define MAX_PACKET_SIZE 100
#define BOOT_NUMBER 2
#define MAX_UCHAR (2<<7)-1
#define MAX_USHORT (2<<15)-1
#define BEACON_OFFSET 2
#define ADVERT_OFFSET 2
#define DISTANCE_OFFSET 4
#define DATA_OFFSET 4

#define LINK_AD_FACTOR 8
#define PATH_AD_FACTOR 8
#define DATA_FACTOR 6

#define BEACON_PERIOD_MS  10000
#define BEACON_JITTER_MS  1000

// neighbor table stores address of neighbor, number of received beacons from it and the bi-directional link quality
neighbor_t table[NEIGHBOR_NUMBER];
// R = max_pkt(r(h(pkt->src)))
u_char R = 0;
// actual distance to sink D
u_short D = MAX_USHORT;
// actual father on route to sink
u_short V = 0;
// tree_round is the number of the actual spanning tree construction
u_char tree_round = 0;
// packets
ccc_packet_t *beacon_pkt, *advert_pkt, *distance_pkt, *data_pkt;

u_short node_addr;

u_char send_distance = 0;

u_char I_AM_SINK = 0;

/**
* Returns a 16-bit ID based on the Bluetooth address
 *
 * The Bluetooth address is cached in eeprom
 */
u_short getID(struct btstack *stack) {
    
    short len = 0; 
    long value;
    bt_addr_t addr;
    
    // get id from EEPROM
    len = btn_eepromdb_get( FOURCC('b','t','i', 'd'), 4, &value);
    if (len) {
        return (u_short) value;
    }
    // otherwise, start bt
    if (!stack) {
        // bluetooth module on (takes a while)
        btn_hardware_bt_on();
        
        // Start the stack and let the initialization begin
        stack = bt_hci_init(&BT_UART);
    }                            
    
    // get bt address
    bt_hci_get_local_bt_addr(stack, addr);
    
    // store id
    value = FOURCC( addr[3], addr[2], addr[1], addr[0]);
    btn_eepromdb_store(FOURCC('b','t','i', 'd'), 4, &value);	
    
    return (u_short) value;
}

void init_printf(void){
    // serial baud rate
    u_long baud = 57600;
    
    // init terminal app uart
    NutRegisterDevice(&APP_UART, 0, 0);
    freopen(APP_UART.dev_name, "r+", stdout);
    _ioctl(_fileno(stdout), UART_SETSPEED, &baud);
}    

void init_radio(u_short addr) {
	bmac_init(addr);
	bmac_enable_led(1);
	ccc_init(&bmac_interface);
	// reduce rf-power for demonstration purpose
	cc1000_set_RF_power(1);
}

// h = number of ones in binary form
u_int h(u_short i) {
	u_int count = 0;
	do {
		if (i%2) {
			count++;
		}
		i = i/2;
	} 
	while (i!=0);
	return count;
}

// r = number of zeros at end of binary form
u_int r(u_int i) {
	u_int count = 0;
	do {
		if (i%2) {
			return count;
		}
		else {
			count++;
		}
		i = i/2;
	}
	while (i!=0);
	// for 0 return 1
	return 1;
}

/* void beacon_handler(ccc_packet_t *pkt) {
	u_short addr = pkt->src;
	u_int i = 0;
	u_char changed = 0;
	if (r(h(addr)) > R) {
		R = r(h(addr));
	}
	u_int r = rand() % (1<<(R+1));
	// am i already in?
	for (i=0; i<NEIGHBOR_NUMBER; i++) {
		if (table[i].address == addr) {
			table[i].number++;
			changed = 1;
			break;
		}
	}	
	// if not already in
	if (!changed) {	
		// with probability P=NEIGHBOR_NUMBER/2^(R+1) do something
		if (r < NEIGHBOR_NUMBER) {
			for (i=0; i<NEIGHBOR_NUMBER; i++) {
				if (table[i].number == 0) {
					neighbor_t n;
					n.address = addr;
					n.number = 1;
					table[i] = n;
					changed = 1;
					break;
				}
			}
			// otherwise decrement the value of all neighbors in the list
			if (!changed) {
				for (i=0; i<NEIGHBOR_NUMBER; i++) {
					table[i].number--;
				}
			}
		}
	}
    // print neighbour quality
    printf("Neighbours Table: \n");
	for (i=0; i<NEIGHBOR_NUMBER; i++) {
        printf("%03x (%u), ", table[i].address, table[i].number);
    }
    printf("\n");
    
}
*/

#define BEACON_COUNT_MAX (LINK_AD_FACTOR * 2)

void beacon_handler(ccc_packet_t *pkt) {
	u_short addr = pkt->src;
	u_char inTable = 0;
	u_char i;
	
	// am i already in?
	for (i=0; i<NEIGHBOR_NUMBER; i++) {
		if (table[i].address == addr) {
		
			if (table[i].numberThisRound < LINK_AD_FACTOR) {
				table[i].numberThisRound++;		// for incoming quality
			}
			
			if (table[i].number < BEACON_COUNT_MAX) {
				table[i].number++;			// for table stuff
			}
			inTable = 1;
			break;
		}
	}	
	
	// is there place for me?
	if (!inTable) {	
		// space in table
		for (i=0; i<NEIGHBOR_NUMBER; i++) {
			if (table[i].number == 0) {
				table[i].address = addr;
				table[i].number  = 1;
				table[i].numberThisRound = 1;
				inTable = 1;
				break;
			}
		}
	}
	
	// if no space in table, just decrease others 
	// this only works, as long as there are less new neighbours than table places
	if (!inTable){
		// otherwise decrement the value of all neighbors in the list
		for (i=0; i<NEIGHBOR_NUMBER; i++) {
			if (table[i].number > 0){
				table[i].number--;
			}
		}
	}
	
    // print neighbour quality
    printf("Neighbours Table: \n");
	for (i=0; i<NEIGHBOR_NUMBER; i++) {
        printf("%03x (q %u, c %u), ", table[i].address,  table[i].numberThisRound, table[i].number);
    }
    printf("\n");
    
}


void advert_handler(ccc_packet_t *pkt) {
	u_char i, j;
	u_short addr;
    printf("advert handler -- sender %u\n", pkt->src);
	for (i=0; i<NEIGHBOR_NUMBER; i++) {
		addr = ntohs( * ((u_short *) &pkt->data[ADVERT_OFFSET+(i*3)])  );
        printf("advert: it's about %03x, qual %u\n",  addr, pkt->data[ADVERT_OFFSET+(i*3)+2]);
		// if i am in the list
		if (addr == node_addr) {
			// find table entry
			for (j=0;j<NEIGHBOR_NUMBER; j++) {
				// adapt bidirectional quality
				if (table[j].address == pkt->src) {
//					table[j].quality = ((MAX_UCHAR-pkt->data[ADVERT_OFFSET+(i*3)+2])*(MAX_UCHAR-table[j].number))>>8;
					table[j].quality = ((1+LINK_AD_FACTOR-pkt->data[ADVERT_OFFSET+(i*3)+2])*(1+LINK_AD_FACTOR-table[j].incomingQuality));
                    printf("advert: updated quality of %03x. %u to %u\n", j, table[j].address, table[j].quality); 
					break;
				}
			}
			break;
		}
	}
}


u_short best_D;
u_short best_V;

void distance_handler(ccc_packet_t *pkt) {
	u_char i;
	
	// worst link quality
	u_char quality = MAX_UCHAR;
	// worst path quality 
	u_short l = MAX_USHORT;
	
	// get path cost from packet
	u_short pkt_cost = ntohs( * (u_short *) &pkt->data[DISTANCE_OFFSET]);
	
	// do we have link estimate to this guy?
	for (i=0; i<NEIGHBOR_NUMBER; i++) {
		if (table[i].address == pkt->src) {
			quality = table[i].quality;
			break;
		}
	}
	// we have it.
	// another bug: if quality is 65530 and link is 6 we'll get a perfect link!
	if (quality < MAX_UCHAR && pkt_cost < (MAX_USHORT - 0x100)) { 
		// l = distance of neighbor + plus link weight to this neighbor
		l = pkt_cost + quality;
		
		// if better than currently used V -> take it
		if (l < D){
			D = l;
			V = pkt->src;
			best_D = l;
			best_V = pkt->src;
		}
		
		// maybe best in this round ?
		else if (l < best_D){
			best_D = l;
			best_V = pkt->src;
		}
		// if this link reduces distance to sink or a new routing tree construction is initialized
        printf("distance: new quality to sink %u, old %u\n", l, D);
	}
	
	// if new round
	if (pkt->data[DISTANCE_OFFSET+2] != tree_round) {

		// accept best of last round
		D = best_D;
		V = best_V;
		// use new path from this path adv
		best_D = l;
		best_V = pkt->src;

		// current round started
		tree_round = pkt->data[DISTANCE_OFFSET+2];
	
		// send path adv packet -- but only if decent path 
		if (D < (MAX_USHORT - 0x100) ) {

			// copy sink address -- WRONG: :) but doesn't matter. there's only one sink.
			*(u_short *) &distance_pkt->data[2] = *(u_short *) &pkt->data[2];
			// set quality and round
			*(u_short *) &distance_pkt->data[DISTANCE_OFFSET]= htons(D);
			distance_pkt->data[DISTANCE_OFFSET+2] = tree_round;

			// request distance packet sending
			send_distance = 1;
		}
	}
}

void data_handler(ccc_packet_t *pkt) {
	// if i am not the sink and i have a route to sink
	if ( I_AM_SINK == 0 && V != 0) {
		ccc_send(V, DATA_TYPE, pkt);
	}
}

void main(void) {
	u_char i;
    char temp;
	u_short seqNr = 0;
    u_short mhopSeqNr = 0;
	int err;
	
	// initialize btnode hardware
	btn_hardware_init();
	btn_led_init(1);

    init_printf();
    printf("mc routing booting..\n");

    // get node addr
    node_addr = getID(NULL);
    
    // use addr for random
    srand( node_addr );

	// set sink state for node 234 and another one
	if (node_addr == 0xe0){
		I_AM_SINK = 1;
	}

    printf("ID %03x", node_addr);
    printf("Version "PROGRAM_VERSION"\n");
    printf("AppVersion = %u. \n",APP_VERSION);
    printf("I_AM_SINK  = %u\n", I_AM_SINK);
	printf("battery = %u 1/1000 V\n", btn_bat_measure(10));
    
    // init radio
    printf("init cc1000...\n");
    init_radio(node_addr);

    // INSOMNIA! sleep mode causes chipcon reception to collapse
    NutThreadSetSleepMode(SLEEP_MODE_NONE);

    // init sensors
    printf("init btsense...\n");
	btsense_init(BTSENSE_REVISION_1_1);


	// register packet handlers
    printf("register handlers...\n");
	ccc_register_packet_handler(BEACON_TYPE, beacon_handler);
	ccc_register_packet_handler(ADVERT_TYPE, advert_handler);
	if ( I_AM_SINK == 0) {
		ccc_register_packet_handler(DISTANCE_TYPE, distance_handler);
	}
	ccc_register_packet_handler(DATA_TYPE, data_handler);
	
	// initialize table qualities to avoid routing loops
	for(i=0;i<NEIGHBOR_NUMBER;i++) {
		table[i].quality = MAX_UCHAR;
		table[i].number = 0;
		table[i].numberThisRound = 0;
		table[i].incomingQuality = 0;
	}

	// construct invariabel parts of packets
	beacon_pkt = new_ccc_packet(MAX_PACKET_SIZE);
	* (u_short *) &beacon_pkt->data[0] = htons( node_addr );
	
	advert_pkt = new_ccc_packet(MAX_PACKET_SIZE);
	advert_pkt->length = ADVERT_OFFSET + (3 * NEIGHBOR_NUMBER);
	* (u_short *) &advert_pkt->data[0] = htons( node_addr );

	distance_pkt = new_ccc_packet(MAX_PACKET_SIZE);
	distance_pkt->length = DISTANCE_OFFSET + 3;
	* (u_short *) &distance_pkt->data[0] = htons( node_addr );
	if ( I_AM_SINK ) {
        * (u_short *)  &distance_pkt->data[2] = htons( node_addr );
		* (u_short * ) &distance_pkt->data[DISTANCE_OFFSET] = 0;
	}
    
	data_pkt = new_ccc_packet(MAX_PACKET_SIZE);
	* (u_short *) &data_pkt->data[0] = htons( node_addr );

	u_char link_ad_count = LINK_AD_FACTOR-1;
	u_char path_ad_count = (PATH_AD_FACTOR/2)+1;
	u_char data_count = 1;
	
	V = 0;
	D = MAX_USHORT;

    printf("running.\n");

	for(;;) {
	
		// construct and send beacon
		* (u_short *) &beacon_pkt->data[BEACON_OFFSET]   = htons( seqNr );
		* (u_short *) &beacon_pkt->data[BEACON_OFFSET+2] = htons ( btn_bat_measure(10) );
		beacon_pkt->data[BEACON_OFFSET+4] = APP_VERSION; 
		beacon_pkt->length = BEACON_OFFSET + 5;
        ccc_send(BROADCAST_ADDR, BEACON_TYPE, beacon_pkt);
		if (seqNr == (2<<15)-1) {
			seqNr = 100;
		}
		else {
			seqNr++;
		}
		NutSleep(BEACON_PERIOD_MS/3);

		if (link_ad_count < LINK_AD_FACTOR) {
			link_ad_count++;
		}
		else {
			// construct and send link advertisement
			for (i=0; i<NEIGHBOR_NUMBER; i++) {
				// for packet
				* (u_short *) &advert_pkt->data[ADVERT_OFFSET+(i*3)] = htons( table[i].address );
                advert_pkt->data[ADVERT_OFFSET+(i*3)+2] = table[i].numberThisRound;
				// house keeping
				table[i].incomingQuality = table[i].numberThisRound;
			    table[i].numberThisRound = 0;
			    table[i].number /= 2; // exponential moving average... 
			}
			ccc_send(BROADCAST_ADDR, ADVERT_TYPE, advert_pkt);
			link_ad_count = 1;
		}
		NutSleep(BEACON_PERIOD_MS/3);

		// if i am the sink, initialize tree construction
		if ( I_AM_SINK ) {
			if (path_ad_count < PATH_AD_FACTOR) {
				path_ad_count++;
				
			}
			else {
				// construct and send route advertisement
				tree_round++;
				distance_pkt->data[DISTANCE_OFFSET+2] = tree_round;
				ccc_send(BROADCAST_ADDR, DISTANCE_TYPE, distance_pkt);
				path_ad_count = 1;
			}
		}
		// if i am not the sink, send data 
		else {
			if (data_count < DATA_FACTOR) {
				data_count++;
				// check if distance should be send
				if (send_distance) {
					printf("distance: send update now \n");
					ccc_send(BROADCAST_ADDR, DISTANCE_TYPE, distance_pkt);
					send_distance = 0;
				}
			} 
			else {	
				if (V != 0) {
					// get temperature and send it to sink
					err = btsense_sample_temp(&temp);
					data_pkt->length = DATA_OFFSET + 1;
                    * ((u_short *) &data_pkt->data[2]) = htons( mhopSeqNr );
					if (err) {
						data_pkt->data[DATA_OFFSET] = 0;
					}
					else {
						data_pkt->data[DATA_OFFSET] = temp;
					}
					ccc_send(V, DATA_TYPE, data_pkt);
					
					printf("Data: send to %u, seq %u\n", V, mhopSeqNr);
					mhopSeqNr++;
				}
				data_count = 1;
			}
		}
		NutSleep(BEACON_PERIOD_MS/3+ (rand() % BEACON_JITTER_MS));
	}
}


