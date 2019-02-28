<?php

namespace App\Http\Controllers;

use App\Models\Contact;
use App\Models\Event;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use phpDocumentor\Reflection\Types\Compound;

class HomeController extends Controller
{
    /**
     * Create a new controller instance.
     *
     * @return void
     */
    public function __construct()
    {
        $this->middleware('auth');
    }

    /**
     * Show the application dashboard.
     *
     * @return \Illuminate\Contracts\Support\Renderable
     */
    public function index()
    {
        $events = Event::orderByDesc('date')->paginate(15);
        $aggquery = Contact::join('event_agenda_item_contacts','contacts.id','=','event_agenda_item_contacts.contactId')
            ->join('events','event_agenda_item_contacts.eventId','=','events.id')
            ->select('contacts.id','sex','category','groupId');
        $totalagg = clone $aggquery;
        $genderagg = clone $aggquery;
        $categoryagg = clone $aggquery;
        $groupagg = clone $aggquery;
        $total = $totalagg->select(DB::raw('count(1) as total'))->pluck('total')->first() ?? 0;
        $bygender = $genderagg->select('sex',DB::raw('count(1) as count'))->groupBy('sex')->get();
        $bycategory = $categoryagg->select('category',DB::raw('count(1) as count'))->groupBy('category')->get();
        $bygroupid = $groupagg->select('groupId',DB::raw('count(1) as count'))->groupBy('groupId')->orderByDesc('count')->limit(15)->get();
        return view('home')->with(compact('events'))
            ->with(compact('total'))
            ->with(compact('bygender'))
            ->with(compact('bycategory'))
            ->with(compact('bygroupid'));
    }
}
