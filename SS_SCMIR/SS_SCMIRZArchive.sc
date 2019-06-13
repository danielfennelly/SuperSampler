//ZArchive no longer in SC core, so reintroduced here. Originally written by Chris Sattinger as part of the crucial library

SS_SCMIRZArchive : File {

	var stringCache,<>path,<>version=1.0;
	var lastItem,count=0,stringCacheStart;

	*write { arg pathName;
		^super.new(pathName, "wb").path_(pathName).check.initStringCache
	}
	*read { arg pathName;
		^super.new(pathName, "rb").path_(pathName).check
			.getStringCache
	}
	writeItem { arg thing, extraArgs;
		var check;
		// == has some problems
		check = lastItem === thing;

		if(check,{
			count = count + 1;
		},{
			lastItem = thing;
			this.saveCount;
			thing.writeSCMIRZArchive(this,extraArgs);
		});
	}
	readItem { arg class; // if class not nil, it will assert that the item is of that class

		var type,size,thing,classname;
		if(count > 0,{
			count = count - 1;
			^lastItem
		});
		type = this.getChar;

		if(type == $.,{ // ... repetition
			count = this.getInt32 - 1;
			^lastItem
		});

		if(type == $N,{
			if(class.notNil and: (Nil !== class),{
				die("ZArchive got wrong type:",nil,"expected:",class.asString);
			});
			^lastItem = nil
		});
		if(type == $F,{
			thing = this.getFloat;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(type == $I,{
			thing = this.getInt32;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(type == $s,{ // small string < 128
			thing = this.readString;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(type == $S,{ // large string
			thing = this.readLargeString;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(type == $y,{ // symbol
			thing = this.readString.asSymbol;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(type == $D,{// dictionaries
			classname = this.readString;
			size = this.getInt32;
			thing = classname.asSymbol.asClass.new(size);
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			size.do({ arg i;
				thing.put(this.readItem,this.readItem);
			});
			^lastItem = thing
		});
		if(type == $C,{ // collection
			classname = this.readString;
			size = this.getInt32;
			thing = classname.asSymbol.asClass.new(size);
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			size.do({ arg i;
				thing.add( this.readItem );
			});
			^lastItem = thing
		});
		if(type == $x,{ // small compile string
			thing = this.readString.interpret;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(type == $X,{ // large compile string
			thing = this.readLargeString.interpret;
			if(class.notNil and: (thing.class !== class),{
				die("ZArchive got wrong type:",thing,"expected:",class.asString);
			});
			^lastItem = thing
		});
		if(this.pos >= (stringCacheStart),{
			this.close;
			"End of file, unable to read item".die(this);
		});
		this.close;
		die("ZArchive read error, got bad type code:",type);
	}

	writeClose {
		var stringCacheStart;
		this.saveCount;
		stringCacheStart = this.pos;

		// if you do go over the limit, you could simply raise the limit but your old files aren't compatible
		if(stringCache.size >= (2**16),{
			"Too many strings in archive !  Not supported.".die;
		});

		this.putInt16(stringCache.size);
		stringCache.keysValuesDo({ |string,index|
			this.putInt16(index);
			this.putInt8(string.size);
			this.putString(string);
		});
		this.putInt32(stringCacheStart);

		this.close;
	}

	//PRIVATE
	saveCount {
		if(count > 0,{
			super.putChar($.);
			super.putInt32(count);
			count = 0;
		});
	}
	check {
		if(this.isOpen.not,{ "ZArchive failed to open".die(path); });
	}
	initStringCache { stringCache = Dictionary.new }
	getStringCache {
		var size;
		this.seek(-4,2); // bytes from the end
		stringCacheStart = this.getInt32;

		this.seek(stringCacheStart,0);
		size = this.getInt16;
		stringCache = Array.newClear(size);
		size.do({ arg i;
			var index,ssize,string;
			index = this.getInt16;
			ssize = this.getInt8;
			/*if(ssize < 0,{
				Error("ZArchive read error.  String table returns negative sized item").throw;
			});*/
			string = String.newClear(ssize);
			this.read(string);
			stringCache.put( index, string );
		});
		this.seek(0,0);

		// the one thing you will never archive is the archiver itself
		// so lastItem starts with this
		lastItem = this;
		// any other item in existence is something you might potentially save next
		// eg. Object, nil
	}
	writeString { arg string;
		var prev;
		prev = stringCache[string];
		if(prev.isNil,{
			prev = stringCache.size;
			stringCache[string] = prev;
		});
		this.putInt16(prev);
	}
	readString {
		var ix,string;
		ix = this.getInt16;
		^stringCache[ix] ?? {"ZArchive failed to find String : index:".die(ix);};
	}
	writeLargeString { arg string;
		this.putInt32(string.size);
		this.putString(string);
	}
	readLargeString {
		var size,string;
		size = this.getInt32;
		string = String.newClear(size);
		this.read(string);
		^string
	}
	asZArchive { ^this }
}
/*
 to detect repeats of dictionaries (or events) and arrays:
  instead of internally using writeItem/readItem, use prWriteItem,prReadItem.
this would break current archives, so you could use a different type code
 for the new ones
*/
