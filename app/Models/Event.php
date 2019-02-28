<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Event extends Model
{
    //
    public function location()
    {
        return $this->belongsTo(Location::class,'locationId');
    }

    public function children()
    {
        return $this->hasMany(EventAgendaItem::class,'eventId','id');
    }
}
