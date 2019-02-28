<?php
/**
 * Created by PhpStorm.
 * User: karim
 * Date: 2/27/19
 * Time: 8:42 PM
 */

namespace App\Http\View\Composers;
use Illuminate\View\View;
use App\Models\EventType;


class EventTypeComposer
{
    public function compose(View $view)
    {
        $eventTypes = EventType::all();
        $view->with(compact('eventTypes'));
    }
}